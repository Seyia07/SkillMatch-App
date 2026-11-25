from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import emailer
from ..config import settings
from ..db import get_db
from ..deps import get_current_user
from ..models import OtpCode, RefreshToken, User, now
from ..security import (
    create_jwt, decode_jwt, hash_otp, hash_password, hash_token, is_allowed_email, make_otp,
    make_participant_code, new_refresh_token, normalise_email, otp_matches, password_problem,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])

GENERIC_SENT = "If this address can be used, a code has been sent to it."
BAD_LOGIN = "Invalid email or password."


# ---------- helpers ----------
def _domain_check(email: str) -> str:
    e = normalise_email(email)
    if not is_allowed_email(e):
        raise HTTPException(400, f"Please use your @{settings.allowed_email_domain} email address.")
    return e


def _issue_otp(db: Session, email: str, purpose: str) -> str:
    window = now() - timedelta(minutes=settings.otp_window_minutes)
    recent = db.scalars(
        select(OtpCode).where(OtpCode.email == email, OtpCode.purpose == purpose, OtpCode.created_at >= window)
    ).all()
    if len(recent) >= settings.otp_requests_per_window:
        raise HTTPException(429, "Too many code requests. Please wait a few minutes and try again.")
    for old in db.scalars(select(OtpCode).where(OtpCode.email == email, OtpCode.purpose == purpose, OtpCode.used == False)):  # noqa: E712
        old.used = True
    code = make_otp()
    db.add(OtpCode(email=email, purpose=purpose, code_hash=hash_otp(email, purpose, code),
                   expires_at=now() + timedelta(minutes=settings.otp_ttl_minutes)))
    db.commit()
    subject = {"signup": "Your SkillMatch sign-up code", "reset": "Your SkillMatch password reset code",
               "admin_login": "Your SkillMatch admin login code"}[purpose]
    emailer.send_email(email, subject,
                       f"Your code is {code}. It expires in {settings.otp_ttl_minutes} minutes and works once.\n"
                       "If you did not ask for it, ignore this message.")
    return code


def _check_otp(db: Session, email: str, purpose: str, code: str) -> bool:
    row = db.scalars(
        select(OtpCode).where(OtpCode.email == email, OtpCode.purpose == purpose, OtpCode.used == False)  # noqa: E712
        .order_by(OtpCode.id.desc())
    ).first()
    if row is None or row.expires_at < now() or row.attempts >= settings.otp_max_attempts:
        return False
    row.attempts += 1
    ok = otp_matches(row.code_hash, email, purpose, code)
    if ok:
        row.used = True
    db.commit()
    return ok


def _user_dict(u: User) -> dict:
    return {"id": u.id, "email": u.email, "year_of_study": u.year_of_study, "programme": u.programme,
            "is_admin": u.is_admin, "participant_code": u.participant_code}


def issue_tokens(db: Session, user: User) -> dict:
    refresh = new_refresh_token()
    db.add(RefreshToken(user_id=user.id, token_hash=hash_token(refresh),
                        expires_at=now() + timedelta(days=settings.refresh_token_days)))
    db.commit()
    return {"access_token": create_jwt("access", str(user.id), settings.access_token_minutes),
            "refresh_token": refresh, "token_type": "bearer",
            "expires_in": settings.access_token_minutes * 60, "user": _user_dict(user)}


def _revoke_all(db: Session, user_id: int) -> None:
    for t in db.scalars(select(RefreshToken).where(RefreshToken.user_id == user_id, RefreshToken.revoked == False)):  # noqa: E712
        t.revoked = True
    db.commit()


# ---------- sign up ----------
class EmailIn(BaseModel):
    email: str


class CodeIn(BaseModel):
    email: str
    code: str = Field(min_length=4, max_length=10)


class CompleteIn(BaseModel):
    signup_token: str
    password: str
    year_of_study: int = Field(ge=1, le=6)
    programme: str = Field(default="", max_length=120)
    consent: bool


@router.post("/signup/request")
def signup_request(body: EmailIn, db: Session = Depends(get_db)):
    email = _domain_check(body.email)
    out = {"message": GENERIC_SENT}
    if db.scalars(select(User).where(User.email == email)).first() is None:
        code = _issue_otp(db, email, "signup")
        if settings.dev_echo_otp:
            out["dev_code"] = code
    return out


@router.post("/signup/verify")
def signup_verify(body: CodeIn, db: Session = Depends(get_db)):
    email = _domain_check(body.email)
    if not _check_otp(db, email, "signup", body.code):
        raise HTTPException(400, "That code is wrong or has expired. Request a new one.")
    return {"signup_token": create_jwt("signup", email, 15)}


@router.post("/signup/complete")
def signup_complete(body: CompleteIn, db: Session = Depends(get_db)):
    email = decode_jwt(body.signup_token, "signup")
    if not email:
        raise HTTPException(400, "Your sign-up session expired. Please start again.")
    if not body.consent:
        raise HTTPException(400, "You must accept the consent and privacy notice to create an account.")
    problem = password_problem(body.password)
    if problem:
        raise HTTPException(400, problem)
    if db.scalars(select(User).where(User.email == email)).first():
        raise HTTPException(400, "This account already exists. Please log in.")
    user = User(email=email, password_hash=hash_password(body.password), year_of_study=body.year_of_study,
                programme=body.programme.strip(), consent_at=now(), participant_code=make_participant_code())
    db.add(user)
    db.commit()
    db.refresh(user)
    return issue_tokens(db, user)


# ---------- login ----------
class LoginIn(BaseModel):
    email: str
    password: str


class MfaIn(BaseModel):
    mfa_token: str
    code: str


class RefreshIn(BaseModel):
    refresh_token: str
    all_devices: bool = False


@router.post("/login")
def login(body: LoginIn, db: Session = Depends(get_db)):
    email = normalise_email(body.email)
    user = db.scalars(select(User).where(User.email == email)).first()
    if user and user.locked_until and user.locked_until > now():
        raise HTTPException(423, "Too many failed attempts. Try again in a few minutes or reset your password.")
    if not verify_password(body.password, user.password_hash if user else None):
        if user:
            user.failed_attempts += 1
            if user.failed_attempts >= settings.max_failed_logins:
                user.locked_until = now() + timedelta(minutes=settings.lockout_minutes)
                user.failed_attempts = 0
            db.commit()
        raise HTTPException(401, BAD_LOGIN)
    user.failed_attempts, user.locked_until = 0, None
    db.commit()
    if user.is_admin:
        _issue_otp(db, email, "admin_login")
        return {"mfa_required": True, "mfa_token": create_jwt("mfa", email, settings.otp_ttl_minutes)}
    return issue_tokens(db, user)


@router.post("/login/mfa")
def login_mfa(body: MfaIn, db: Session = Depends(get_db)):
    email = decode_jwt(body.mfa_token, "mfa")
    user = db.scalars(select(User).where(User.email == email)).first() if email else None
    if not user or not user.is_admin or not _check_otp(db, email, "admin_login", body.code):
        raise HTTPException(401, "That code is wrong or has expired.")
    return issue_tokens(db, user)


@router.post("/refresh")
def refresh(body: RefreshIn, db: Session = Depends(get_db)):
    row = db.scalars(select(RefreshToken).where(RefreshToken.token_hash == hash_token(body.refresh_token))).first()
    if row is None or row.revoked or row.expires_at < now():
        raise HTTPException(401, "Your session has expired. Please log in again.")
    row.revoked = True
    db.commit()
    return issue_tokens(db, db.get(User, row.user_id))


@router.post("/logout")
def logout(body: RefreshIn, db: Session = Depends(get_db)):
    row = db.scalars(select(RefreshToken).where(RefreshToken.token_hash == hash_token(body.refresh_token))).first()
    if row:
        if body.all_devices:
            _revoke_all(db, row.user_id)
        else:
            row.revoked = True
            db.commit()
    return {"message": "Logged out."}


# ---------- passwords ----------
class ResetIn(BaseModel):
    email: str
    code: str
    new_password: str


class ChangeIn(BaseModel):
    old_password: str
    new_password: str


@router.post("/password/forgot")
def password_forgot(body: EmailIn, db: Session = Depends(get_db)):
    email = normalise_email(body.email)
    out = {"message": GENERIC_SENT}
    if is_allowed_email(email) and db.scalars(select(User).where(User.email == email)).first():
        code = _issue_otp(db, email, "reset")
        if settings.dev_echo_otp:
            out["dev_code"] = code
    return out


@router.post("/password/reset")
def password_reset(body: ResetIn, db: Session = Depends(get_db)):
    email = normalise_email(body.email)
    problem = password_problem(body.new_password)
    if problem:
        raise HTTPException(400, problem)
    user = db.scalars(select(User).where(User.email == email)).first()
    if not user or not _check_otp(db, email, "reset", body.code):
        raise HTTPException(400, "That code is wrong or has expired. Request a new one.")
    user.password_hash = hash_password(body.new_password)
    user.failed_attempts, user.locked_until = 0, None
    db.commit()
    _revoke_all(db, user.id)
    return {"message": "Password changed. Please log in."}


@router.post("/password/change")
def password_change(body: ChangeIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if not verify_password(body.old_password, user.password_hash):
        raise HTTPException(400, "Your current password is wrong.")
    problem = password_problem(body.new_password)
    if problem:
        raise HTTPException(400, problem)
    user.password_hash = hash_password(body.new_password)
    db.commit()
    _revoke_all(db, user.id)
    return {"message": "Password changed. Please log in again."}

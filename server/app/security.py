import hashlib
import hmac
import re
import secrets
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from .config import settings

EMAIL_RE = re.compile(r"^[a-z0-9._%+\-]+@([a-z0-9\-]+\.)+[a-z]{2,}$")


def normalise_email(email: str) -> str:
    return email.strip().lower()


def is_allowed_email(email: str) -> bool:
    e = normalise_email(email)
    return bool(EMAIL_RE.match(e)) and e.endswith("@" + settings.allowed_email_domain)


def password_problem(pw: str) -> str | None:
    if len(pw) < settings.min_password_length:
        return f"Password must be at least {settings.min_password_length} characters."
    if len(pw.encode()) > 72:
        return "Password is too long (72 bytes at most)."
    if not re.search(r"[A-Za-z]", pw) or not re.search(r"\d", pw):
        return "Password must contain at least one letter and one number."
    return None


def hash_password(pw: str) -> str:
    return bcrypt.hashpw(pw.encode(), bcrypt.gensalt(rounds=12)).decode()


_DUMMY_HASH = bcrypt.hashpw(b"dummy-password-1", bcrypt.gensalt(rounds=12)).decode()


def verify_password(pw: str, hashed: str | None) -> bool:
    """Always does one bcrypt check, so unknown emails take as long as known ones."""
    target = hashed or _DUMMY_HASH
    try:
        ok = bcrypt.checkpw(pw.encode()[:72], target.encode())
    except ValueError:
        return False
    return ok and hashed is not None


def make_otp() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


def hash_otp(email: str, purpose: str, code: str) -> str:
    return hashlib.sha256(f"{settings.secret_key}|{email}|{purpose}|{code}".encode()).hexdigest()


def otp_matches(stored_hash: str, email: str, purpose: str, code: str) -> bool:
    return hmac.compare_digest(stored_hash, hash_otp(email, purpose, code.strip()))


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def new_refresh_token() -> str:
    return secrets.token_urlsafe(48)


def make_participant_code() -> str:
    return "P-" + secrets.token_hex(3).upper()


def _utc() -> datetime:
    return datetime.now(timezone.utc)


def create_jwt(typ: str, subject: str, minutes: int) -> str:
    payload = {"typ": typ, "sub": subject, "iat": _utc(), "exp": _utc() + timedelta(minutes=minutes)}
    return jwt.encode(payload, settings.secret_key, algorithm="HS256")


def decode_jwt(token: str, typ: str) -> str | None:
    try:
        data = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
    except jwt.PyJWTError:
        return None
    if data.get("typ") != typ:
        return None
    return str(data.get("sub"))

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from ..config import settings
from ..cv import extract_text, suggest_skills
from ..db import get_db
from ..deps import get_current_user
from ..matching import canonical_for
from ..models import RefreshToken, ResearchSession, SurveyResponse, User, UserSkill
from ..security import verify_password
from ..skills_data import SKILLS

router = APIRouter(tags=["me"])


def _profile(u: User) -> dict:
    return {"id": u.id, "email": u.email, "year_of_study": u.year_of_study, "programme": u.programme,
            "units": u.units or [], "skills": sorted(s.name for s in u.skills), "is_admin": u.is_admin,
            "participant_code": u.participant_code}


@router.get("/me")
def get_me(user: User = Depends(get_current_user)):
    return _profile(user)


class ProfileIn(BaseModel):
    year_of_study: int = Field(ge=1, le=6)
    programme: str = Field(default="", max_length=120)
    units: list[str] = Field(default_factory=list, max_length=80)


@router.put("/me/profile")
def put_profile(body: ProfileIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    user.year_of_study = body.year_of_study
    user.programme = body.programme.strip()
    user.units = list(dict.fromkeys(u.strip()[:120] for u in body.units if u.strip()))
    db.commit()
    return _profile(user)


class SkillsIn(BaseModel):
    skills: list[str] = Field(max_length=200)


@router.put("/me/skills")
def put_skills(body: SkillsIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    names: list[str] = []
    for raw in body.skills:
        s = raw.strip()[:120]
        if not s:
            continue
        s = canonical_for(s) or s
        if s.lower() not in {n.lower() for n in names}:
            names.append(s)
    db.execute(delete(UserSkill).where(UserSkill.user_id == user.id))
    db.add_all(UserSkill(user_id=user.id, name=n) for n in names)
    db.commit()
    db.refresh(user)
    return _profile(user)


@router.get("/skills/suggest")
def skills_suggest(q: str = "", user: User = Depends(get_current_user)):
    q = q.strip().lower()
    if len(q) < 1:
        return []
    starts = [n for n in SKILLS if n.lower().startswith(q)]
    contains = [n for n in SKILLS if q in n.lower() and n not in starts]
    return (starts + contains)[:10]


@router.post("/me/cv")
async def upload_cv(file: UploadFile = File(...), user: User = Depends(get_current_user)):
    data = await file.read(settings.max_cv_bytes + 1)
    if len(data) > settings.max_cv_bytes:
        raise HTTPException(413, "That file is too large (2 MB at most).")
    text = extract_text(file.filename or "", data)
    if len(text.split()) < 20:
        raise HTTPException(400, "We could not find readable text in that file. Scanned images are not supported.")
    out = suggest_skills(text)
    out["note"] = "Nothing is saved yet. Confirm or edit these skills. The file itself was not stored."
    return out


class DeleteIn(BaseModel):
    password: str


@router.post("/me/delete")  # the Android app uses POST because a body on DELETE is unreliable on some phones
@router.delete("/me")
def delete_me(body: DeleteIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if not verify_password(body.password, user.password_hash):
        raise HTTPException(400, "Your password is wrong.")
    code = user.participant_code
    db.execute(delete(RefreshToken).where(RefreshToken.user_id == user.id))
    db.execute(delete(ResearchSession).where(ResearchSession.participant_code == code))
    db.execute(delete(SurveyResponse).where(SurveyResponse.participant_code == code))
    db.delete(user)
    db.commit()
    return {"message": "Your account and data have been deleted."}

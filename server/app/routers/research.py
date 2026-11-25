from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import get_current_user
from ..models import ResearchEvent, ResearchSession, SurveyResponse, User, now

router = APIRouter(prefix="/research", tags=["research"])


class StartIn(BaseModel):
    condition: str = Field(pattern="^(manual|app)$")
    listing_set: str = Field(default="", max_length=10)


class EventIn(BaseModel):
    event: str = Field(min_length=1, max_length=60)
    payload: dict = Field(default_factory=dict)


class EndIn(BaseModel):
    shortlist: list[int] = Field(default_factory=list, max_length=50)
    confidence: int | None = Field(default=None, ge=1, le=5)


class SurveyIn(BaseModel):
    sus: list[int] = Field(min_length=10, max_length=10)
    tam_useful: list[int] = Field(default_factory=list, max_length=10)
    tam_ease: list[int] = Field(default_factory=list, max_length=10)


def _mine(db: Session, user: User, sid: int) -> ResearchSession:
    s = db.get(ResearchSession, sid)
    if not s or s.participant_code != user.participant_code:
        raise HTTPException(404, "Session not found.")
    return s


@router.post("/sessions")
def start(body: StartIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    s = ResearchSession(user_id=user.id, participant_code=user.participant_code,
                        condition=body.condition, listing_set=body.listing_set)
    db.add(s)
    db.commit()
    return {"session_id": s.id}


@router.post("/sessions/{sid}/events")
def event(sid: int, body: EventIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    s = _mine(db, user, sid)
    if s.ended_at:
        raise HTTPException(400, "This session has ended.")
    db.add(ResearchEvent(session_id=s.id, event=body.event, payload=body.payload))
    db.commit()
    return {"ok": True}


@router.post("/sessions/{sid}/end")
def end(sid: int, body: EndIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    s = _mine(db, user, sid)
    if s.ended_at:
        raise HTTPException(400, "This session has already ended.")
    s.ended_at, s.shortlist, s.confidence = now(), body.shortlist, body.confidence
    db.commit()
    return {"seconds": round((s.ended_at - s.started_at).total_seconds(), 1)}


@router.post("/survey")
def survey(body: SurveyIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    for v in body.sus + body.tam_useful + body.tam_ease:
        if not 1 <= v <= 5:
            raise HTTPException(400, "Answers must be between 1 and 5.")
    db.add(SurveyResponse(participant_code=user.participant_code, sus=body.sus,
                          tam_useful=body.tam_useful, tam_ease=body.tam_ease))
    db.commit()
    return {"message": "Thank you."}

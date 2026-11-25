import csv
import io
from datetime import date, datetime

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import require_admin
from ..flags import compute_flags
from ..matching import find_skills, split_requirements
from ..models import Listing, Report, Requirement, ResearchEvent, ResearchSession, SurveyResponse, User

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])

SOURCES = "^(partner_list|careers_email|career_page|job_platform|referral|other)$"


class ListingIn(BaseModel):
    title: str = Field(min_length=3, max_length=200)
    organisation: str = Field(min_length=2, max_length=200)
    type: str = Field(pattern="^(internship|wbl)$")
    source: str = Field(pattern=SOURCES)
    source_url: str = ""
    apply_url: str = ""
    contact_email: str = ""
    description: str = Field(min_length=30)
    closing_date: date | None = None
    research_set: str = Field(default="", pattern="^(|A|B)$")


def _admin_view(l: Listing) -> dict:
    return {
        "id": l.id, "title": l.title, "organisation": l.organisation, "type": l.type, "source": l.source,
        "source_url": l.source_url, "apply_url": l.apply_url, "contact_email": l.contact_email,
        "description": l.description, "closing_date": l.closing_date.isoformat() if l.closing_date else None,
        "status": l.status, "research_set": l.research_set, "flags": l.flags or [], "date_added": l.date_added.isoformat(),
        "requirements": [{"id": r.id, "text": r.text, "skills": r.skills or []} for r in l.requirements],
    }


def _apply(l: Listing, data: ListingIn) -> None:
    for k in ("title", "organisation", "type", "source", "source_url", "apply_url", "contact_email", "description", "closing_date", "research_set"):
        setattr(l, k, getattr(data, k))
    l.flags = compute_flags(l.type, l.description, l.source_url, l.apply_url, l.contact_email, l.closing_date)


def _fill_requirements(l: Listing) -> None:
    l.requirements.clear()
    for i, r in enumerate(split_requirements(l.description)):
        l.requirements.append(Requirement(position=i, text=r["text"], skills=r["skills"]))


def _get(db: Session, listing_id: int) -> Listing:
    l = db.get(Listing, listing_id)
    if not l:
        raise HTTPException(404, "Listing not found.")
    return l


@router.post("/listings")
def create_listing(body: ListingIn, db: Session = Depends(get_db)):
    l = Listing(status="pending")
    _apply(l, body)
    _fill_requirements(l)
    db.add(l)
    db.commit()
    db.refresh(l)
    return _admin_view(l)


@router.get("/listings")
def list_all(status: str | None = None, db: Session = Depends(get_db)):
    stmt = select(Listing).order_by(Listing.id.desc())
    if status:
        stmt = stmt.where(Listing.status == status)
    return [_admin_view(l) for l in db.scalars(stmt)]


@router.get("/listings/{listing_id}")
def get_listing(listing_id: int, db: Session = Depends(get_db)):
    return _admin_view(_get(db, listing_id))


@router.put("/listings/{listing_id}")
def update_listing(listing_id: int, body: ListingIn, db: Session = Depends(get_db)):
    l = _get(db, listing_id)
    changed = l.description != body.description
    _apply(l, body)
    if changed:
        _fill_requirements(l)
    db.commit()
    db.refresh(l)
    return _admin_view(l)


class ReqsIn(BaseModel):
    requirements: list[str] = Field(min_length=1, max_length=20)


@router.put("/listings/{listing_id}/requirements")
def put_requirements(listing_id: int, body: ReqsIn, db: Session = Depends(get_db)):
    l = _get(db, listing_id)
    l.requirements.clear()
    for i, text in enumerate(t.strip() for t in body.requirements if t.strip()):
        l.requirements.append(Requirement(position=i, text=text[:400], skills=find_skills(text)))
    db.commit()
    db.refresh(l)
    return _admin_view(l)


@router.post("/listings/{listing_id}/publish")
def publish(listing_id: int, db: Session = Depends(get_db)):
    l = _get(db, listing_id)
    if not l.requirements:
        raise HTTPException(400, "Add at least one requirement before publishing.")
    if l.closing_date and l.closing_date < date.today():
        raise HTTPException(400, "The closing date has passed.")
    l.flags = compute_flags(l.type, l.description, l.source_url, l.apply_url, l.contact_email, l.closing_date)
    l.status = "published"
    db.commit()
    return _admin_view(l)


@router.post("/listings/{listing_id}/remove")
def remove(listing_id: int, db: Session = Depends(get_db)):
    l = _get(db, listing_id)
    l.status = "removed"
    db.commit()
    return _admin_view(l)


@router.get("/reports")
def reports(status: str = "open", db: Session = Depends(get_db)):
    rows = db.scalars(select(Report).where(Report.status == status).order_by(Report.id.desc())).all()
    return [{"id": r.id, "listing_id": r.listing_id, "reason": r.reason, "note": r.note,
             "created_at": r.created_at.isoformat()} for r in rows]


class ResolveIn(BaseModel):
    action: str = Field(pattern="^(dismiss|remove_listing)$")


@router.post("/reports/{report_id}/resolve")
def resolve(report_id: int, body: ResolveIn, db: Session = Depends(get_db)):
    r = db.get(Report, report_id)
    if not r:
        raise HTTPException(404, "Report not found.")
    r.status = "resolved"
    if body.action == "remove_listing":
        l = db.get(Listing, r.listing_id)
        if l:
            l.status = "removed"
    db.commit()
    return {"message": "Report resolved."}


@router.get("/stats")
def stats(db: Session = Depends(get_db)):
    by_status = dict(db.execute(select(Listing.status, func.count()).group_by(Listing.status)).all())
    return {"users": db.scalar(select(func.count()).select_from(User)) or 0, "listings": by_status,
            "open_reports": db.scalar(select(func.count()).select_from(Report).where(Report.status == "open")) or 0}


def _csv(rows: list[list], header: list[str]) -> PlainTextResponse:
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(header)
    w.writerows(rows)
    return PlainTextResponse(buf.getvalue(), media_type="text/csv")


@router.get("/export/sessions.csv")
def export_sessions(db: Session = Depends(get_db)):
    rows = []
    for s in db.scalars(select(ResearchSession).order_by(ResearchSession.id)):
        secs = round((s.ended_at - s.started_at).total_seconds(), 1) if s.ended_at else ""
        n_events = db.scalar(select(func.count()).select_from(ResearchEvent).where(ResearchEvent.session_id == s.id))
        rows.append([s.id, s.participant_code, s.condition, s.listing_set, s.started_at.isoformat(),
                     secs, ";".join(map(str, s.shortlist or [])), s.confidence if s.confidence is not None else "", n_events])
    return _csv(rows, ["session_id", "participant", "condition", "listing_set", "started_at",
                       "seconds", "shortlist_listing_ids", "confidence_1to5", "events"])


def sus_score(items: list[int]) -> float | None:
    if len(items) != 10:
        return None
    return round(sum((v - 1) if i % 2 == 0 else (5 - v) for i, v in enumerate(items)) * 2.5, 1)


@router.get("/export/survey.csv")
def export_survey(db: Session = Depends(get_db)):
    rows = []
    for s in db.scalars(select(SurveyResponse).order_by(SurveyResponse.id)):
        mean = lambda xs: round(sum(xs) / len(xs), 2) if xs else ""
        rows.append([s.id, s.participant_code, sus_score(s.sus or []) or "", mean(s.tam_useful or []), mean(s.tam_ease or []),
                     s.created_at.isoformat()])
    return _csv(rows, ["id", "participant", "sus_0to100", "tam_useful_mean", "tam_ease_mean", "created_at"])

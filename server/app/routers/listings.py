from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import get_current_user
from ..fitmodel import get_model
from ..matching import FitResult, evaluate, student_evidence
from ..models import Listing, Report, SavedListing, User

router = APIRouter(tags=["listings"])

NOTE = "Fit shows how your skills cover the stated requirements. It does not predict whether you will be selected."
REPORT_REASONS = {"fee_requested", "scam_or_fake", "expired", "wrong_information", "other"}


def _evidence(user: User):
    """No fit is shown until the student has added skills or units (a programme alone says too little)."""
    if not user.skills and not user.units:
        return [], set()
    return student_evidence([s.name for s in user.skills], user.units or [], user.programme)


def _open(db: Session):
    today = date.today()
    return select(Listing).where(Listing.status == "published",
                                 or_(Listing.closing_date.is_(None), Listing.closing_date >= today))


def _resume_text(user: User) -> str:
    """The student's profile written as plain text, which is what the trained model reads."""
    parts = []
    if user.programme:
        parts.append(f"Studying {user.programme}.")
    if user.units:
        parts.append("Units: " + ", ".join(user.units) + ".")
    if user.skills:
        parts.append("Skills: " + ", ".join(s.name for s in user.skills) + ".")
    return " ".join(parts)


def _fit(user: User, listing: Listing, evidence, canon) -> FitResult | None:
    if not evidence:
        return None
    reqs = [{"text": r.text, "skills": r.skills or []} for r in listing.requirements]
    fit = evaluate(reqs, evidence, canon, [r["text"] for r in reqs])  # per-requirement Met / Partly met / Missing
    model = get_model()
    if model is not None:  # the trained model gives the overall verdict and the ranking score
        pred = model.predict(_resume_text(user), listing.description)
        fit.overall, fit.label = pred.score, pred.label
        fit.ai = {"label": pred.label, "probabilities": pred.probabilities, "model": "Logistic Regression + matching features"}
    return fit


def _summary(l: Listing, fit: FitResult | None, saved: bool) -> dict:
    return {
        "id": l.id, "title": l.title, "organisation": l.organisation, "type": l.type, "source": l.source,
        "source_url": l.source_url, "apply_url": l.apply_url, "date_added": l.date_added.isoformat(),
        "closing_date": l.closing_date.isoformat() if l.closing_date else None,
        "flags": l.flags or [], "saved": saved,
        "fit": None if fit is None else {"overall": fit.overall, "label": fit.label, "met": fit.met,
                                          "partly": fit.partly, "missing": fit.missing, "ai": fit.ai},
    }


def _saved_ids(db: Session, user: User) -> set[int]:
    return set(db.scalars(select(SavedListing.listing_id).where(SavedListing.user_id == user.id)))


@router.get("/listings")
def list_listings(
    type: str | None = Query(None, pattern="^(internship|wbl)$"),
    q: str | None = None,
    closing_within_days: int | None = Query(None, ge=1, le=365),
    min_fit: int | None = Query(None, ge=0, le=100),
    sort: str = Query("fit", pattern="^(fit|closing|new)$"),
    research_set: str | None = Query(None, pattern="^(A|B)$"),
    include_fit: bool = True,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    stmt = _open(db)
    if type:
        stmt = stmt.where(Listing.type == type)
    if closing_within_days:
        stmt = stmt.where(Listing.closing_date.is_not(None),
                          Listing.closing_date <= date.today() + timedelta(days=closing_within_days))
    if research_set:
        stmt = stmt.where(Listing.research_set == research_set)
    if q and q.strip():
        like = f"%{q.strip().lower()}%"
        stmt = stmt.where(or_(Listing.title.ilike(like), Listing.organisation.ilike(like), Listing.description.ilike(like)))
    listings = db.scalars(stmt).all()
    evidence, canon = _evidence(user)
    saved = _saved_ids(db, user)
    rows = []
    for l in listings:
        f = _fit(user, l, evidence, canon) if include_fit else None
        if min_fit is not None and (f is None or f.overall is None or f.overall < min_fit):
            continue
        rows.append((l, f))
    far = date.max
    if sort == "closing":
        rows.sort(key=lambda x: x[0].closing_date or far)
    elif sort == "new":
        rows.sort(key=lambda x: x[0].date_added, reverse=True)
    else:
        rows.sort(key=lambda x: -(x[1].overall if x[1] and x[1].overall is not None else -1))
    return {
        "needs_skills": include_fit and not evidence,
        "count": len(rows),
        "items": [_summary(l, f, l.id in saved) for l, f in rows],
    }


def _published(db: Session, listing_id: int) -> Listing:
    l = db.scalars(_open(db).where(Listing.id == listing_id)).first()
    if not l:
        raise HTTPException(404, "Listing not found or no longer open.")
    return l


@router.get("/listings/{listing_id}")
def listing_detail(listing_id: int, include_fit: bool = True, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    l = _published(db, listing_id)
    evidence, canon = _evidence(user) if include_fit else ([], set())
    fit = _fit(user, l, evidence, canon)
    out = _summary(l, fit, listing_id in _saved_ids(db, user))
    out.update({
        "description": l.description, "contact_email": l.contact_email, "note": NOTE,
        "needs_skills": include_fit and not evidence,
        "engine": fit.engine if fit else None,
        "ai": fit.ai if fit else None,
        "requirements": [
            {"text": r.text, "status": r.status, "score": r.score, "matched_with": r.matched_with, "note": r.note}
            for r in (fit.requirements if fit else [])
        ] or [{"text": r.text, "status": None, "score": None, "matched_with": None, "note": ""} for r in l.requirements],
    })
    return out


@router.post("/listings/{listing_id}/save")
def save_listing(listing_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    _published(db, listing_id)
    if not db.scalars(select(SavedListing).where(SavedListing.user_id == user.id, SavedListing.listing_id == listing_id)).first():
        db.add(SavedListing(user_id=user.id, listing_id=listing_id))
        db.commit()
    return {"saved": True}


@router.delete("/listings/{listing_id}/save")
def unsave_listing(listing_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    row = db.scalars(select(SavedListing).where(SavedListing.user_id == user.id, SavedListing.listing_id == listing_id)).first()
    if row:
        db.delete(row)
        db.commit()
    return {"saved": False}


@router.get("/me/saved")
def my_saved(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    ids = _saved_ids(db, user)
    evidence, canon = _evidence(user)
    items = []
    for l in db.scalars(select(Listing).where(Listing.id.in_(ids), Listing.status == "published")):
        items.append(_summary(l, _fit(user, l, evidence, canon), True))
    return {"count": len(items), "items": items}


class ReportIn(BaseModel):
    reason: str
    note: str = ""


@router.post("/listings/{listing_id}/report")
def report_listing(listing_id: int, body: ReportIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.reason not in REPORT_REASONS:
        raise HTTPException(400, "Unknown reason.")
    _published(db, listing_id)
    db.add(Report(listing_id=listing_id, user_id=user.id, reason=body.reason, note=body.note[:500]))
    db.commit()
    return {"message": "Thank you. An admin will review this listing."}

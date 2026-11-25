"""Start-up helpers: create the first admin and the SAMPLE listings. Used by the scripts and, when set, at server start."""
import logging
from datetime import date, timedelta

from .db import Base, SessionLocal, engine
from .flags import compute_flags
from .matching import split_requirements
from .models import Listing, Requirement, User, now
from .security import hash_password, is_allowed_email, make_participant_code, normalise_email, password_problem

log = logging.getLogger("skillmatch")

D = date.today()
SAMPLES = [
    ("[SAMPLE] Software Development Intern", "Sample Company A", "internship", "career_page", "https://example.com/careers", "https://example.com/careers/apply", "jobs@example.com", D + timedelta(days=30),
     "Join our product team for a 3 month internship.\n\nRequirements:\n- Python, SQL and Git\n- Good communication skills\n- Currently pursuing a degree in Computer Science or a related field\n- Experience with REST APIs\n\nResponsibilities\n- Build small features with a mentor\n- Write unit tests"),
    ("[SAMPLE] Data Analyst Intern", "Sample Company B", "internship", "careers_email", "https://example.org", "", "careers@example.org", D + timedelta(days=21),
     "Support our analytics team.\n\nQualifications:\n- Excel and Power BI\n- Knowledge of statistics\n- Attention to detail\n- Ability to work in a team\n\nWhat we offer\n- Mentorship and a stipend"),
    ("[SAMPLE] WBL Placement: Mobile Developer", "Sample Company C", "wbl", "partner_list", "https://example.net", "https://example.net/apply", "hr@example.net", D + timedelta(days=45),
     "An 8 week work based learning placement (320 hours) from January.\n\nRequirements:\n- Kotlin and Android development\n- Git\n- Problem solving\n- Familiarity with Jetpack Compose\n\nResponsibilities:\n- Work on our Android app"),
    ("[SAMPLE] WBL Placement: IT Support", "Sample Company D", "wbl", "partner_list", "https://example.com/d", "", "it@example.com", D + timedelta(days=40),
     "A 10 week placement supporting our IT team.\n\nRequirements:\n- Networking and Windows Server\n- Troubleshooting\n- Customer service\n- Linux\n\nBenefits\n- Lunch provided"),
    ("[SAMPLE] Accounting Intern", "Sample Company E", "internship", "job_platform", "https://example.com/e", "https://example.com/e/apply", "finance@example.com", D + timedelta(days=14),
     "Requirements:\n- Accounting and Excel\n- QuickBooks\n- Financial reporting\n- Attention to detail\n\nDuties\n- Prepare monthly reports"),
    ("[SAMPLE] Marketing Attachment (do not apply)", "Sample Company F", "wbl", "other", "", "", "recruiter@gmail.com", D + timedelta(days=5),
     "Apply by WhatsApp only. A registration fee of KES 2,000 is payable before the 4 week placement starts.\n\nRequirements:\n- Digital marketing\n- Photocopying and filing\n- Communication"),
]



def ensure_admin(email: str, password: str) -> str:
    """Creates the admin, or promotes the account and resets its password. Returns a short status line."""
    email = normalise_email(email)
    if not is_allowed_email(email):
        raise ValueError("Use an email on the allowed domain.")
    problem = password_problem(password)
    if problem:
        raise ValueError(problem)
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        u = db.query(User).filter(User.email == email).first()
        if u:
            u.is_admin, u.password_hash = True, hash_password(password)
        else:
            db.add(User(email=email, password_hash=hash_password(password), is_admin=True, year_of_study=3,
                        consent_at=now(), participant_code=make_participant_code()))
        db.commit()
    return f"Admin ready: {email} (every admin login also needs an emailed code)"


def seed_samples() -> int:
    """Adds the SAMPLE listings once. Returns how many were added."""
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        if db.query(Listing).filter(Listing.title.like("[SAMPLE]%")).count():
            return 0
        for n, (title, org, typ, src, surl, aurl, mail, closing, desc) in enumerate(SAMPLES):
            l = Listing(title=title, organisation=org, type=typ, source=src, source_url=surl, apply_url=aurl,
                        contact_email=mail, description=desc, closing_date=closing, status="published",
                        flags=compute_flags(typ, desc, surl, aurl, mail, closing),
                        research_set="AB"[n % 2])  # alternate sets so the study screens have listings
            for i, r in enumerate(split_requirements(desc)):
                l.requirements.append(Requirement(position=i, text=r["text"], skills=r["skills"]))
            db.add(l)
        db.commit()
        return len(SAMPLES)


def run_startup_tasks(admin_email: str, admin_password: str, seed: bool) -> None:
    """Never stops the server from starting: problems are logged instead."""
    if admin_email and admin_password:
        try:
            log.warning(ensure_admin(admin_email, admin_password))
        except ValueError as e:
            log.error("ADMIN_EMAIL / ADMIN_PASSWORD not used: %s", e)
    if seed:
        added = seed_samples()
        if added:
            log.warning("Added %d SAMPLE listings.", added)

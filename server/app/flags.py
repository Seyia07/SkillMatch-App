"""Warning flags shown next to a listing. They flag possible problems; they never prove anything."""
import re
from datetime import date
from urllib.parse import urlparse

FEE = re.compile(
    r"(registration|application|processing|training|admin(?:istration)?|placement|interview|vetting|uniform)\s+fee"
    r"|pay\s+(?:a\s+|an\s+)?(?:fee|deposit)|\bdeposit\b|send\s+(?:us\s+)?money|m-?pesa\s+(?:till|paybill|number)", re.I)
MESSAGING = re.compile(r"whats\s*app|telegram|signal\s+app|dm\s+us|inbox\s+us", re.I)
EMAIL = re.compile(r"[\w.+\-]+@[\w\-]+\.[\w.\-]+")
PERSONAL_MAIL = {"gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "icloud.com", "proton.me", "protonmail.com"}
WEAK_SITES = {"facebook.com", "instagram.com", "wa.me", "t.me", "linktr.ee", "bit.ly", "tinyurl.com", "docs.google.com", "forms.gle"}
WBL_BAD_WORK = re.compile(r"photocop|personal assistant|\bsecretar|clerical|receptionist|family business|own company", re.I)
WEEKS = re.compile(r"(\d{1,2})\s*(?:-|to)?\s*(\d{1,2})?\s*weeks?", re.I)


def _host(url: str) -> str:
    try:
        h = (urlparse(url if "//" in url else "//" + url).hostname or "").lower()
    except ValueError:
        return ""
    return h[4:] if h.startswith("www.") else h


def compute_flags(listing_type: str, description: str, source_url: str, apply_url: str,
                  contact_email: str, closing_date: date | None, today: date | None = None) -> list[dict]:
    today = today or date.today()
    text = f"{description}\n{contact_email}"
    flags: list[dict] = []

    def add(code: str, severity: str, message: str) -> None:
        flags.append({"code": code, "severity": severity, "message": message})

    if FEE.search(text):
        add("fee_requested", "warning", "The listing seems to ask for a fee or payment. Genuine placements do not charge students.")
    emails = EMAIL.findall(text)
    if MESSAGING.search(text) and not emails:
        add("messaging_only", "warning", "Contact is by a messaging app only, with no email address.")
    if any(e.split("@")[-1].lower() in PERSONAL_MAIL for e in emails):
        add("personal_email", "warning", "Contact uses a personal email address, not an organisation address.")
    hosts = [_host(u) for u in (source_url, apply_url) if u]
    if not hosts or all(h in WEAK_SITES or not h for h in hosts):
        add("no_website", "warning", "No organisation website link was given.")
    if listing_type == "wbl":
        if WBL_BAD_WORK.search(description):
            add("wbl_rules", "warning", "May not count as WBL (the attachment rules exclude photocopying, personal assistant and family business work). Check with the Attachment Office.")
        for m in WEEKS.finditer(description):
            top = int(m.group(2) or m.group(1))
            if top < 8:
                add("wbl_short", "warning", "The duration looks shorter than the 8 weeks (320 hours) WBL requires.")
                break
    if closing_date and 0 <= (closing_date - today).days <= 7:
        add("closing_soon", "info", "Closing within 7 days.")
    return flags

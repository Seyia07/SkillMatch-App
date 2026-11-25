"""Sends one-time codes. With no SMTP_HOST set, codes are printed to the console (development)."""
import logging
import smtplib
from email.message import EmailMessage

from .config import settings

log = logging.getLogger("skillmatch.email")
outbox: list[dict] = []  # kept in memory so tests and demos can read the last message


def send_email(to: str, subject: str, body: str) -> None:
    outbox.append({"to": to, "subject": subject, "body": body})
    del outbox[:-50]
    if not settings.smtp_host:
        log.warning("EMAIL (not sent, no SMTP configured) to=%s subject=%s\n%s", to, subject, body)
        return
    msg = EmailMessage()
    msg["From"], msg["To"], msg["Subject"] = settings.smtp_from, to, subject
    msg.set_content(body)
    try:
        with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as s:
            s.starttls()
            if settings.smtp_user:
                s.login(settings.smtp_user, settings.smtp_password)
            s.send_message(msg)
    except Exception:  # never leak mail errors to the caller
        log.exception("Failed to send email to %s", to)

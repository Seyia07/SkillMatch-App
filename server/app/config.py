"""Settings read from environment variables (see .env.example)."""
import os


def _flag(name: str, default: str = "0") -> bool:
    return os.getenv(name, default).strip().lower() in ("1", "true", "yes")


class Settings:
    def __init__(self) -> None:
        self.database_url = os.getenv("DATABASE_URL", "sqlite:///./skillmatch.db")
        self.secret_key = os.getenv("SECRET_KEY", "dev-only-change-me")
        self.allowed_email_domain = os.getenv("ALLOWED_EMAIL_DOMAIN", "strathmore.edu").lower()
        self.otp_ttl_minutes = int(os.getenv("OTP_TTL_MINUTES", "10"))
        self.otp_max_attempts = int(os.getenv("OTP_MAX_ATTEMPTS", "5"))
        self.otp_requests_per_window = int(os.getenv("OTP_REQUESTS_PER_WINDOW", "3"))
        self.otp_window_minutes = int(os.getenv("OTP_WINDOW_MINUTES", "15"))
        self.max_failed_logins = int(os.getenv("MAX_FAILED_LOGINS", "5"))
        self.lockout_minutes = int(os.getenv("LOCKOUT_MINUTES", "15"))
        self.access_token_minutes = int(os.getenv("ACCESS_TOKEN_MINUTES", "60"))
        self.refresh_token_days = int(os.getenv("REFRESH_TOKEN_DAYS", "14"))
        self.min_password_length = int(os.getenv("MIN_PASSWORD_LENGTH", "8"))
        self.smtp_host = os.getenv("SMTP_HOST", "")
        self.smtp_port = int(os.getenv("SMTP_PORT", "587"))
        self.smtp_user = os.getenv("SMTP_USER", "")
        self.smtp_password = os.getenv("SMTP_PASSWORD", "")
        self.smtp_from = os.getenv("SMTP_FROM", "SkillMatch <no-reply@example.com>")
        self.dev_echo_otp = _flag("DEV_ECHO_OTP")
        self.embedder = os.getenv("EMBEDDER", "tfidf").lower()
        self.sbert_model = os.getenv("SBERT_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
        # Cut-offs for Met / Partly met. Placeholders until calibrated on expert-rated profiles.
        self.met_cutoff = float(os.getenv("MET_CUTOFF", "0.70"))
        self.partly_cutoff = float(os.getenv("PARTLY_CUTOFF", "0.40"))
        # Hosts without a shell (free tiers) create the admin and sample listings from settings at start-up.
        self.admin_email = os.getenv("ADMIN_EMAIL", "").strip()
        self.admin_password = os.getenv("ADMIN_PASSWORD", "")
        self.seed_demo = _flag("SEED_DEMO")
        self.max_cv_bytes = int(os.getenv("MAX_CV_BYTES", str(2 * 1024 * 1024)))


settings = Settings()

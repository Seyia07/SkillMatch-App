# SkillMatch server

The back end for the SkillMatch Android app: accounts, curated listings, skill profiles and the
per-requirement fit service described in the proposal (Python, FastAPI, SQL database).

## Run it (development)

```bash
python -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                 # then edit SECRET_KEY (Windows: copy .env.example .env)
set -a; source .env; set +a          # load the settings (or set them in your IDE)
python -m scripts.seed_demo          # optional: adds SAMPLE listings to try the app
python -m scripts.create_admin you@strathmore.edu 'ChooseAStrongPass1'
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

* API docs open at http://localhost:8000/docs
* The Android emulator reaches your computer at `http://10.0.2.2:8000`. A real phone on the same
  Wi-Fi uses your computer's address, for example `http://192.168.1.20:8000`.
* With no `SMTP_HOST`, one-time codes are **printed in the server console**. For a demo without
  email set `DEV_ECHO_OTP=1` and the app can show the code. Never do that in production.
* Run the tests with `python -m pytest -q`.

## What it does

| Area | Behaviour |
|---|---|
| Sign up | Only `@strathmore.edu` addresses. Emailed 6 digit code (10 minutes, single use, 5 tries, 3 requests per 15 minutes), then the student sets a password and accepts the consent notice. |
| Login | Email and password (bcrypt). 5 wrong tries lock the account for 15 minutes. Same error for unknown email and wrong password. Access token (1 hour) plus rotating refresh token (14 days). |
| Passwords | Forgot and reset by emailed code, change password, reset revokes every session. |
| Admins | Separate accounts. Every admin login also needs an emailed code. Created with `scripts.create_admin`. |
| Profile | Year, programme, units, skills. CV upload (PDF, DOCX, TXT) is read in memory and only suggests skills. Nothing is saved until the student confirms. The file is never stored. |
| Listings | Added by admins, split into requirements, checked, then published. Hidden after the closing date. Each has a source, dates, warning flags and a report button. |
| Fit | For each requirement: Met, Partly met or Missing, plus an overall fit and a ranked list. Filters: type, closing soon, minimum fit, search. |
| Research | Timed sessions (manual or app), events, confidence, SUS and TAM answers. Admin CSV exports use participant codes, not emails. |
| Privacy | Delete my account removes the profile, skills, saved listings and research data. No gender, age or ethnicity is collected or used. |

## Matching, honestly

* Skills are normalised with a small hand made vocabulary (`app/skills_data.py`). It is not the full
  ESCO list. Add the skills that appear in your real listings.
* A requirement and a student skill that name the same skill match fully. A skill in the same family
  (for example MySQL and PostgreSQL) earns **partial** credit only. Java is never treated as JavaScript.
* Requirements with no recognised skill are compared by text similarity. The default engine is TF-IDF
  on character n-grams, which needs no downloads. Set `EMBEDDER=sbert` and install
  `sentence-transformers` to use Sentence-BERT as the proposal describes (not tested in this build).
* `MET_CUTOFF` (0.70) and `PARTLY_CUTOFF` (0.40) are **placeholders**. Calibrate them on the
  expert-rated calibration profiles before the evaluation.
* Fit is not a prediction of selection, and the app says so.

## Before real use

* Put the server behind HTTPS (a reverse proxy or a host that provides it).
* Set a long random `SECRET_KEY` and real SMTP settings.
* Use PostgreSQL (`DATABASE_URL`, plus `pip install "psycopg[binary]"`) if more than a demo.
* Replace the SAMPLE listings with real, checked ones.

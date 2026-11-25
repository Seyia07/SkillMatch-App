from datetime import date, timedelta

from app import emailer

from .conftest import auth, last_code, make_admin, signup

API = "/api/v1"
LISTING = {
    "title": "Software Intern", "organisation": "Acme Ltd", "type": "internship", "source": "career_page",
    "source_url": "https://acme.example.com/careers", "apply_url": "https://acme.example.com/apply",
    "contact_email": "jobs@acme.example.com",
    "description": "We are hiring an intern.\n\nRequirements:\n- Python, SQL and Docker\n- Good communication skills\n- Experience with PostgreSQL databases\n\nResponsibilities\n- Build dashboards",
    "closing_date": (date.today() + timedelta(days=20)).isoformat(),
}


def admin_tokens(client):
    email, pw = make_admin()
    r = client.post(f"{API}/auth/login", json={"email": email, "password": pw}).json()
    assert r["mfa_required"] is True
    t = client.post(f"{API}/auth/login/mfa", json={"mfa_token": r["mfa_token"], "code": last_code()})
    assert t.status_code == 200
    return t.json()


# ---------- accounts ----------
def test_only_school_emails_can_sign_up(client):
    r = client.post(f"{API}/auth/signup/request", json={"email": "someone@gmail.com"})
    assert r.status_code == 400
    assert not emailer.outbox


def test_signup_needs_correct_code_strong_password_and_consent(client):
    email = "a@strathmore.edu"
    client.post(f"{API}/auth/signup/request", json={"email": email})
    assert client.post(f"{API}/auth/signup/verify", json={"email": email, "code": "000000"}).status_code == 400
    tok = client.post(f"{API}/auth/signup/verify", json={"email": email, "code": last_code()}).json()["signup_token"]
    base = {"signup_token": tok, "year_of_study": 2, "programme": "BBIT"}
    assert client.post(f"{API}/auth/signup/complete", json={**base, "password": "short1", "consent": True}).status_code == 400
    assert client.post(f"{API}/auth/signup/complete", json={**base, "password": "allletters", "consent": True}).status_code == 400
    assert client.post(f"{API}/auth/signup/complete", json={**base, "password": "Passw0rd!x", "consent": False}).status_code == 400
    assert client.post(f"{API}/auth/signup/complete", json={**base, "password": "Passw0rd!x", "consent": True}).status_code == 200


def test_code_works_once_and_existing_accounts_get_no_code(client):
    signup(client)
    n = len(emailer.outbox)
    r = client.post(f"{API}/auth/signup/request", json={"email": "student@strathmore.edu"})
    assert r.status_code == 200 and len(emailer.outbox) == n  # same reply, no email sent


def test_code_request_rate_limit(client):
    codes = [client.post(f"{API}/auth/signup/request", json={"email": "r@strathmore.edu"}).status_code for _ in range(4)]
    assert codes == [200, 200, 200, 429]


def test_login_lockout_and_generic_error(client):
    signup(client)
    for _ in range(5):
        r = client.post(f"{API}/auth/login", json={"email": "student@strathmore.edu", "password": "wrong-pass1"})
        assert r.status_code == 401
    assert client.post(f"{API}/auth/login", json={"email": "student@strathmore.edu", "password": "Passw0rd!x"}).status_code == 423
    unknown = client.post(f"{API}/auth/login", json={"email": "nobody@strathmore.edu", "password": "wrong-pass1"})
    assert unknown.status_code == 401 and unknown.json()["detail"] == "Invalid email or password."


def test_login_refresh_rotation_and_logout(client):
    t = signup(client)
    login = client.post(f"{API}/auth/login", json={"email": "student@strathmore.edu", "password": "Passw0rd!x"}).json()
    assert client.get(f"{API}/me", headers=auth(login)).status_code == 200
    new = client.post(f"{API}/auth/refresh", json={"refresh_token": login["refresh_token"]}).json()
    assert client.post(f"{API}/auth/refresh", json={"refresh_token": login["refresh_token"]}).status_code == 401  # rotated
    client.post(f"{API}/auth/logout", json={"refresh_token": new["refresh_token"]})
    assert client.post(f"{API}/auth/refresh", json={"refresh_token": new["refresh_token"]}).status_code == 401
    assert client.get(f"{API}/me").status_code == 401


def test_forgot_and_reset_password(client):
    t = signup(client)
    client.post(f"{API}/auth/password/forgot", json={"email": "student@strathmore.edu"})
    code = last_code()
    assert client.post(f"{API}/auth/password/reset", json={"email": "student@strathmore.edu", "code": "111111", "new_password": "NewPassw0rd"}).status_code == 400
    assert client.post(f"{API}/auth/password/reset", json={"email": "student@strathmore.edu", "code": code, "new_password": "NewPassw0rd"}).status_code == 200
    assert client.post(f"{API}/auth/login", json={"email": "student@strathmore.edu", "password": "Passw0rd!x"}).status_code == 401
    assert client.post(f"{API}/auth/login", json={"email": "student@strathmore.edu", "password": "NewPassw0rd"}).status_code == 200
    assert client.post(f"{API}/auth/refresh", json={"refresh_token": t["refresh_token"]}).status_code == 401  # old sessions revoked
    # unknown email gets the same reply and no email
    n = len(emailer.outbox)
    assert client.post(f"{API}/auth/password/forgot", json={"email": "ghost@strathmore.edu"}).status_code == 200
    assert len(emailer.outbox) == n


def test_admin_login_needs_emailed_code_and_students_cannot_use_admin(client):
    s = signup(client)
    assert client.get(f"{API}/admin/stats", headers=auth(s)).status_code == 403
    a = admin_tokens(client)
    assert client.get(f"{API}/admin/stats", headers=auth(a)).status_code == 200


# ---------- profile, CV ----------
def test_profile_skills_and_cv(client):
    s = signup(client)
    h = auth(s)
    r = client.put(f"{API}/me/profile", json={"year_of_study": 3, "programme": "BBIT", "units": ["Database Systems"]}, headers=h)
    assert r.status_code == 200
    r = client.put(f"{API}/me/skills", json={"skills": ["python", "ms office", "Basket weaving"]}, headers=h).json()
    assert r["skills"] == ["Basket weaving", "Microsoft Office", "Python"]
    cv = b"Jane Doe\nProfile\nFinal year student with experience building Python and SQL tools and Git workflows. " \
         b"Worked in a team on an Android app using Kotlin. Strong communication and leadership.\nSkills\nPython, Kotlin, Public speaking, Chess\nEducation\nStrathmore"
    out = client.post(f"{API}/me/cv", files={"file": ("cv.txt", cv)}, headers=h).json()
    assert {"Python", "SQL", "Kotlin", "Git"} <= set(out["matched"])
    assert "Chess" in out["other"]
    assert client.post(f"{API}/me/cv", files={"file": ("cv.exe", b"x" * 50)}, headers=h).status_code == 400
    assert client.get(f"{API}/me", headers=h).json()["skills"] == ["Basket weaving", "Microsoft Office", "Python"]  # nothing saved by the upload


# ---------- listings and matching ----------
def publish(client, a, body=None):
    created = client.post(f"{API}/admin/listings", json=body or LISTING, headers=auth(a))
    assert created.status_code == 200, created.text
    lid = created.json()["id"]
    assert client.post(f"{API}/admin/listings/{lid}/publish", headers=auth(a)).status_code == 200
    return lid, created.json()


def test_listing_requirements_are_split_and_reviewed_before_publish(client):
    a = admin_tokens(client)
    created = client.post(f"{API}/admin/listings", json=LISTING, headers=auth(a)).json()
    assert created["status"] == "pending"
    texts = [r["text"] for r in created["requirements"]]
    assert texts[:3] == ["Python", "SQL", "Docker"] and "Build dashboards" not in texts
    s = signup(client)
    assert client.get(f"{API}/listings", headers=auth(s)).json()["count"] == 0  # pending listings are hidden


def test_fit_per_requirement(client):
    a = admin_tokens(client)
    lid, _ = publish(client, a)
    s = signup(client)
    h = auth(s)
    first = client.get(f"{API}/listings", headers=h).json()
    assert first["needs_skills"] is True and first["items"][0]["fit"] is None
    client.put(f"{API}/me/skills", json={"skills": ["Python", "MySQL", "Communication"]}, headers=h)
    d = client.get(f"{API}/listings/{lid}", headers=h).json()
    status = {r["text"]: r["status"] for r in d["requirements"]}
    assert status["Python"] == "Met"
    assert status["SQL"] == "Partly met"          # MySQL is related, not the same
    assert status["Docker"] == "Missing"
    assert status["Good communication skills"] == "Met"
    assert d["fit"]["met"] == 2 and d["fit"]["partly"] == 2 and d["fit"]["missing"] == 1
    assert "does not predict" in d["note"]


def test_java_is_not_javascript(client):
    a = admin_tokens(client)
    body = {**LISTING, "description": "Requirements:\n- Java\n- Experience building mobile apps for customers"}
    lid, _ = publish(client, a, body)
    s = signup(client)
    client.put(f"{API}/me/skills", json={"skills": ["JavaScript"]}, headers=auth(s))
    d = client.get(f"{API}/listings/{lid}", headers=auth(s)).json()
    java = next(r for r in d["requirements"] if r["text"] == "Java")
    assert java["status"] != "Met"


def test_ranking_filters_save_and_report(client):
    a = admin_tokens(client)
    good, _ = publish(client, a)
    other = {**LISTING, "title": "Finance Intern", "type": "wbl", "description": "Requirements:\n- Accounting and Excel\n- QuickBooks\n- Auditing"}
    weak, _ = publish(client, a, other)
    s = signup(client)
    h = auth(s)
    client.put(f"{API}/me/skills", json={"skills": ["Python", "SQL", "Docker", "Communication", "PostgreSQL"]}, headers=h)
    items = client.get(f"{API}/listings", headers=h).json()["items"]
    assert [i["id"] for i in items] == [good, weak]
    assert client.get(f"{API}/listings?type=wbl", headers=h).json()["count"] == 1
    assert client.get(f"{API}/listings?min_fit=70", headers=h).json()["count"] == 1
    assert client.get(f"{API}/listings?q=finance", headers=h).json()["count"] == 1
    assert client.post(f"{API}/listings/{good}/save", headers=h).json()["saved"] is True
    assert client.get(f"{API}/me/saved", headers=h).json()["count"] == 1
    assert client.delete(f"{API}/listings/{good}/save", headers=h).json()["saved"] is False
    assert client.post(f"{API}/listings/{weak}/report", json={"reason": "scam_or_fake", "note": "looks fake"}, headers=h).status_code == 200
    reports = client.get(f"{API}/admin/reports", headers=auth(a)).json()
    assert len(reports) == 1
    client.post(f"{API}/admin/reports/{reports[0]['id']}/resolve", json={"action": "remove_listing"}, headers=auth(a))
    assert client.get(f"{API}/listings", headers=h).json()["count"] == 1


def test_expired_listings_hide_and_cannot_publish_past_closing(client):
    a = admin_tokens(client)
    old = {**LISTING, "closing_date": (date.today() - timedelta(days=1)).isoformat()}
    lid = client.post(f"{API}/admin/listings", json=old, headers=auth(a)).json()["id"]
    assert client.post(f"{API}/admin/listings/{lid}/publish", headers=auth(a)).status_code == 400


def test_warning_flags(client):
    a = admin_tokens(client)
    bad = {**LISTING, "type": "wbl", "source_url": "", "apply_url": "", "contact_email": "boss@gmail.com",
           "description": "Apply on WhatsApp only. A registration fee of KES 1000 is payable. A 4 week placement.\n\nRequirements:\n- Photocopying and filing\n- Communication"}
    created = client.post(f"{API}/admin/listings", json=bad, headers=auth(a)).json()
    codes = {f["code"] for f in created["flags"]}
    assert {"fee_requested", "personal_email", "no_website", "wbl_rules", "wbl_short"} <= codes
    clean = client.post(f"{API}/admin/listings", json=LISTING, headers=auth(a)).json()
    assert not [f for f in clean["flags"] if f["severity"] == "warning"]


# ---------- research and privacy ----------
def test_research_session_survey_and_exports(client):
    a = admin_tokens(client)
    s = signup(client)
    h = auth(s)
    sid = client.post(f"{API}/research/sessions", json={"condition": "app", "listing_set": "A"}, headers=h).json()["session_id"]
    client.post(f"{API}/research/sessions/{sid}/events", json={"event": "opened_listing", "payload": {"id": 1}}, headers=h)
    assert client.post(f"{API}/research/sessions/{sid}/end", json={"shortlist": [1, 2], "confidence": 4}, headers=h).status_code == 200
    assert client.post(f"{API}/research/sessions/{sid}/end", json={"shortlist": []}, headers=h).status_code == 400
    sus = [5, 1, 5, 1, 5, 1, 5, 1, 5, 1]  # best possible answers score 100
    assert client.post(f"{API}/research/survey", json={"sus": sus, "tam_useful": [4, 5], "tam_ease": [5]}, headers=h).status_code == 200
    csv_s = client.get(f"{API}/admin/export/sessions.csv", headers=auth(a)).text
    assert "app" in csv_s and "1;2" in csv_s
    assert ",100.0," in client.get(f"{API}/admin/export/survey.csv", headers=auth(a)).text


def test_delete_account_removes_everything(client):
    s = signup(client)
    h = auth(s)
    client.post(f"{API}/research/sessions", json={"condition": "manual"}, headers=h)
    assert client.request("DELETE", f"{API}/me", json={"password": "wrong"}, headers=h).status_code == 400
    assert client.request("DELETE", f"{API}/me", json={"password": "Passw0rd!x"}, headers=h).status_code == 200
    assert client.get(f"{API}/me", headers=h).status_code == 401
    assert client.post(f"{API}/auth/login", json={"email": "student@strathmore.edu", "password": "Passw0rd!x"}).status_code == 401
    a = admin_tokens(client)
    assert client.get(f"{API}/admin/export/sessions.csv", headers=auth(a)).text.count("\n") == 1  # header only


def test_delete_account_by_post_for_the_phone_app(client):
    s = signup(client)
    h = auth(s)
    assert client.post(f"{API}/me/delete", json={"password": "wrong"}, headers=h).status_code == 400
    assert client.post(f"{API}/me/delete", json={"password": "Passw0rd!x"}, headers=h).status_code == 200
    assert client.get(f"{API}/me", headers=h).status_code == 401


def test_research_sets_and_hidden_fit(client):
    a = admin_tokens(client)
    in_a, _ = publish(client, a, {**LISTING, "research_set": "A"})
    publish(client, a, {**LISTING, "title": "Other", "research_set": "B"})
    s = signup(client)
    h = auth(s)
    client.put(f"{API}/me/skills", json={"skills": ["Python"]}, headers=h)
    only_a = client.get(f"{API}/listings?research_set=A&include_fit=false", headers=h).json()
    assert [i["id"] for i in only_a["items"]] == [in_a] and only_a["items"][0]["fit"] is None
    d = client.get(f"{API}/listings/{in_a}?include_fit=false", headers=h).json()
    assert d["fit"] is None and all(r["status"] is None for r in d["requirements"])


def test_startup_helpers_and_postgres_url():
    from app.bootstrap import ensure_admin, run_startup_tasks, seed_samples
    from app.db import normalise_db_url
    assert normalise_db_url("postgres://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalise_db_url("postgresql://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalise_db_url("sqlite:///./x.db") == "sqlite:///./x.db"
    assert "Admin ready" in ensure_admin("boss@strathmore.edu", "Passw0rd!x")
    assert "Admin ready" in ensure_admin("boss@strathmore.edu", "Passw0rd!x")  # safe to repeat
    try:
        ensure_admin("boss@gmail.com", "Passw0rd!x")
        assert False
    except ValueError:
        pass
    run_startup_tasks("bad", "bad", True)  # a bad setting is logged, not fatal
    assert seed_samples() == 0  # already added by the line above

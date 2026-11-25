import os
import tempfile

_tmp = tempfile.mkdtemp()
os.environ["DATABASE_URL"] = f"sqlite:///{_tmp}/test.db"
os.environ["SECRET_KEY"] = "test-secret"
os.environ["EMBEDDER"] = "tfidf"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app import emailer  # noqa: E402
from app.db import Base, SessionLocal, engine  # noqa: E402
from app.main import app  # noqa: E402
from app.models import User, now  # noqa: E402
from app.security import hash_password, make_participant_code  # noqa: E402


@pytest.fixture()
def client():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    emailer.outbox.clear()
    with TestClient(app) as c:
        yield c


def last_code() -> str:
    body = emailer.outbox[-1]["body"]
    return body.split("Your code is ")[1].split(".")[0]


def signup(client, email="student@strathmore.edu", password="Passw0rd!x", year=3, programme="BBIT"):
    assert client.post("/api/v1/auth/signup/request", json={"email": email}).status_code == 200
    tok = client.post("/api/v1/auth/signup/verify", json={"email": email, "code": last_code()}).json()["signup_token"]
    r = client.post("/api/v1/auth/signup/complete", json={"signup_token": tok, "password": password,
                                                          "year_of_study": year, "programme": programme, "consent": True})
    assert r.status_code == 200, r.text
    return r.json()


def auth(tokens) -> dict:
    return {"Authorization": f"Bearer {tokens['access_token']}"}


def make_admin(email="admin@strathmore.edu", password="Adm1nPass!"):
    with SessionLocal() as db:
        db.add(User(email=email, password_hash=hash_password(password), is_admin=True, year_of_study=4,
                    consent_at=now(), participant_code=make_participant_code()))
        db.commit()
    return email, password

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse

from .bootstrap import run_startup_tasks
from .config import settings
from .db import Base, engine
from .routers import admin, auth, listings, me, research


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(engine)
    run_startup_tasks(settings.admin_email, settings.admin_password, settings.seed_demo)
    if settings.secret_key == "dev-only-change-me":
        import logging
        logging.getLogger("skillmatch").warning("SECRET_KEY is the development default. Set a real one before deploying.")
    yield


app = FastAPI(title="SkillMatch API", version="1.0.0", lifespan=lifespan)
for r in (auth.router, me.router, listings.router, research.router, admin.router):
    app.include_router(r, prefix="/api/v1")


@app.get("/health")
def health():
    from .fitmodel import get_model
    from .matching import get_engine
    return {"status": "ok", "engine": get_engine().name, "trained_model": get_model() is not None}


@app.get("/admin", include_in_schema=False)
def admin_page():
    """Simple web page for the research team to add, check and publish listings."""
    return FileResponse(Path(__file__).parent / "static" / "admin.html")

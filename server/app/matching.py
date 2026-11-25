"""Skill extraction and per-requirement matching.

Each requirement of a listing gets Met / Partly met / Missing against the student's confirmed skills.
Two signals are combined:
  1. Skill normalisation: a requirement and a student skill that name the same canonical skill match fully;
     a skill in the same family (for example MySQL and PostgreSQL) earns partial credit only.
  2. Text similarity (TF-IDF on character n-grams by default, or Sentence-BERT when EMBEDDER=sbert).
The Met and Partly met cut-offs are placeholders until they are calibrated on expert-rated profiles.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

from .config import settings
from .skills_data import FAMILIES, SKILLS

# ---------- skill detection ----------
_PATTERNS: dict[str, list[re.Pattern]] = {
    name: [re.compile(r"(?<![\w+#])" + re.escape(a) + r"(?![\w+#])", re.I) for a in aliases]
    for name, aliases in SKILLS.items()
}


def find_skills(text: str) -> list[str]:
    """Canonical skills mentioned in text, in order of first appearance."""
    hits: list[tuple[int, str]] = []
    for name, pats in _PATTERNS.items():
        positions = [m.start() for p in pats if (m := p.search(text))]
        if positions:
            hits.append((min(positions), name))
    return [n for _, n in sorted(hits)]


def canonical_for(skill: str) -> str | None:
    """Canonical name when the whole string is one known skill (or alias)."""
    s = skill.strip().lower()
    for name, aliases in SKILLS.items():
        if s in aliases:
            return name
    found = find_skills(skill)
    return found[0] if len(found) == 1 else None


# ---------- splitting a listing into requirements ----------
_BULLET = re.compile(r"^\s*(?:[-*\u2022\u25cf\u25aa\u2013]|\d+[.)])\s*")
_REQ_HEAD = re.compile(
    r"^\s*(requirements?|qualifications?|what you(?:'|\u2019)ll need|what we(?:'|\u2019)re looking for|"
    r"skills(?: and experience)?|you have|you will have|key requirements|minimum requirements|essential|"
    r"must have|desired skills)\b[^.]{0,40}$", re.I)
_OTHER_HEAD = re.compile(
    r"^\s*(responsibilit\w*|duties|benefits|about|what we offer|how to apply|we offer|perks|the role|"
    r"job description|application|who we are)\b[^.]{0,40}$", re.I)
_CUE = re.compile(r"\b(experience|knowledge|proficien|familiar|ability|able to|degree|diploma|must|should|"
                  r"required|skills?|understanding|competen|currently (?:pursuing|studying))\b", re.I)


def _clean(line: str) -> str:
    return re.sub(r"\s+", " ", _BULLET.sub("", line)).strip(" \t-:;,")


def split_requirements(description: str, limit: int = 15) -> list[dict]:
    lines = [ln for ln in description.replace("\r", "").split("\n")]
    collected: list[str] = []
    in_req = False
    for raw in lines:
        ln = raw.strip()
        if not ln:
            continue
        head = ln.rstrip(":")
        if len(head.split()) <= 7 and (ln.endswith(":") or _BULLET.match(ln) is None):
            if _REQ_HEAD.match(head):
                in_req = True
                continue
            if _OTHER_HEAD.match(head):
                in_req = False
                continue
        if in_req:
            collected.append(ln)
    if not collected:  # no clear section: use sentences that sound like requirements
        text = re.sub(r"\s+", " ", description)
        collected = [s for s in re.split(r"(?<=[.;])\s+|\u2022", text) if _CUE.search(s)]

    out: list[dict] = []
    seen: set[str] = set()
    for ln in collected:
        item = _clean(ln)
        if len(item) < 3 or len(item) > 400:
            continue
        pieces = [item]
        skills_in = find_skills(item)
        if len(skills_in) >= 2:  # "Python, SQL and Docker" becomes three requirements
            parts = [_clean(p) for p in re.split(r",|;|\band\b|/", item)]
            parts = [p for p in parts if len(p) >= 2]
            if len(parts) >= 3 and all(len(p.split()) <= 8 for p in parts):
                pieces = parts
        for p in pieces:
            key = p.lower()
            if key in seen:
                continue
            seen.add(key)
            out.append({"text": p, "skills": find_skills(p)})
    return out[:limit]


# ---------- similarity engines ----------
class TfidfEngine:
    name = "tfidf-char"

    def similarity(self, left: list[str], right: list[str], corpus: list[str]) -> np.ndarray:
        vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True).fit(corpus + left + right)
        return (vec.transform(left) @ vec.transform(right).T).toarray()


class SbertEngine:
    name = "sbert"

    def __init__(self) -> None:
        from sentence_transformers import SentenceTransformer  # optional dependency

        self.model = SentenceTransformer(settings.sbert_model)

    def similarity(self, left: list[str], right: list[str], corpus: list[str]) -> np.ndarray:
        a = self.model.encode(left, normalize_embeddings=True)
        b = self.model.encode(right, normalize_embeddings=True)
        return np.clip(a @ b.T, 0.0, 1.0)


_engine = None


def get_engine():
    global _engine
    if _engine is None:
        if settings.embedder == "sbert":
            try:
                _engine = SbertEngine()
            except Exception:  # model or package missing: fall back, and say so in results
                _engine = TfidfEngine()
        else:
            _engine = TfidfEngine()
    return _engine


# ---------- scoring ----------
@dataclass
class ReqResult:
    text: str
    skills: list[str]
    status: str  # Met | Partly met | Missing
    score: float
    matched_with: str | None = None
    note: str = ""


@dataclass
class FitResult:
    overall: int | None
    label: str
    met: int
    partly: int
    missing: int
    requirements: list[ReqResult] = field(default_factory=list)
    engine: str = ""
    ai: dict | None = None  # the trained model's verdict: {"label", "probabilities", "model"}


def _family_of(skill: str) -> set[str]:
    out: set[str] = set()
    for fam in FAMILIES:
        if skill in fam:
            out |= fam
    out.discard(skill)
    return out


def student_evidence(skill_names: list[str], units: list[str], programme: str = "") -> tuple[list[str], set[str]]:
    evidence = [s for s in skill_names if s.strip()] + [u for u in units if u.strip()]
    if programme.strip():  # lets requirements such as "currently pursuing a degree in ..." be answered
        evidence += [f"Currently studying {programme.strip()}", f"Degree in {programme.strip()}"]
    canon: set[str] = set()
    for s in skill_names:
        c = canonical_for(s)
        if c:
            canon.add(c)
        else:
            canon.update(find_skills(s))
    for u in units:
        canon.update(find_skills(u))
    return evidence, canon


def overall_label(pct: int | None) -> str:
    if pct is None:
        return "No requirements found"
    if pct >= 70:
        return "Strong fit"
    if pct >= 40:
        return "Possible fit"
    return "Low fit"


def evaluate(reqs: list[dict], evidence: list[str], canon: set[str], corpus: list[str] | None = None) -> FitResult:
    engine = get_engine()
    if not reqs:
        return FitResult(None, overall_label(None), 0, 0, 0, [], engine.name)
    texts = [r["text"] for r in reqs]
    sims = engine.similarity(texts, evidence, corpus or []) if evidence else np.zeros((len(texts), 1))
    results: list[ReqResult] = []
    for i, r in enumerate(reqs):
        skills = r.get("skills") or []
        best_j = int(np.argmax(sims[i])) if evidence else -1
        sim = float(sims[i][best_j]) if evidence else 0.0
        matched_with, note = (evidence[best_j] if best_j >= 0 else None), ""
        if skills:
            exact = [s for s in skills if s in canon]
            score = len(exact) / len(skills)
            if exact:
                matched_with = exact[0]
            if score < 1.0:
                related = {s: sorted(_family_of(s) & canon) for s in skills if s not in canon}
                got = [s for s, rel in related.items() if rel]
                if got:  # a related skill earns half credit for each unmet skill
                    score = (len(exact) + 0.5 * len(got)) / len(skills)
                    s0 = got[0]
                    matched_with, note = related[s0][0], f"Related skill: {related[s0][0]} (for {s0})"
            if not exact and not note:
                score = min(sim, settings.partly_cutoff - 0.01)  # unrecognised skill: never better than Missing
        else:
            score = sim
        score = max(0.0, min(1.0, score))
        status = "Met" if score >= settings.met_cutoff else "Partly met" if score >= settings.partly_cutoff else "Missing"
        results.append(ReqResult(r["text"], skills, status, round(score, 2),
                                 matched_with if status != "Missing" else None, note))
    met = sum(x.status == "Met" for x in results)
    partly = sum(x.status == "Partly met" for x in results)
    pct = round(100 * (met + 0.5 * partly) / len(results))
    return FitResult(pct, overall_label(pct), met, partly, len(results) - met - partly, results, engine.name)

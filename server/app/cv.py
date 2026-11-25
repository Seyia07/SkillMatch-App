"""Reads text from an uploaded CV in memory and suggests skills. The file is never written to disk."""
import io
import re

from fastapi import HTTPException

from .matching import canonical_for, find_skills

SKILLS_HEAD = re.compile(r"^\s*(technical\s+|key\s+|core\s+)?skills?(?:\s+(?:and|&)\s+\w+)?\s*:?\s*$", re.I)
OTHER_HEAD = re.compile(r"^\s*(experience|education|projects?|references|referees|work|employment|achievements|interests|languages|profile|summary|certifications?)\b.{0,30}$", re.I)


def extract_text(filename: str, data: bytes) -> str:
    name = (filename or "").lower()
    try:
        if name.endswith(".pdf"):
            from pypdf import PdfReader
            return "\n".join((p.extract_text() or "") for p in PdfReader(io.BytesIO(data)).pages)
        if name.endswith(".docx"):
            import docx
            d = docx.Document(io.BytesIO(data))
            parts = [p.text for p in d.paragraphs]
            for t in d.tables:
                parts += [c.text for row in t.rows for c in row.cells]
            return "\n".join(parts)
        if name.endswith(".txt"):
            return data.decode("utf-8", errors="ignore")
    except Exception:
        raise HTTPException(400, "We could not read that file. Try a text-based PDF, a DOCX or a TXT file.")
    raise HTTPException(400, "Please upload a PDF, DOCX or TXT file.")


def suggest_skills(text: str) -> dict:
    matched = find_skills(text)[:60]
    other: list[str] = []
    in_skills = False
    for line in text.splitlines():
        ln = line.strip()
        if not ln:
            continue
        if SKILLS_HEAD.match(ln):
            in_skills = True
            continue
        if OTHER_HEAD.match(ln):
            in_skills = False
            continue
        if in_skills:
            for item in re.split(r"[,;\u2022|/]", ln):
                item = item.strip(" -*:\t")
                if 2 <= len(item) <= 40 and len(item.split()) <= 4 and canonical_for(item) is None \
                        and item.lower() not in {o.lower() for o in other}:
                    other.append(item)
    return {"matched": matched, "other": other[:40]}

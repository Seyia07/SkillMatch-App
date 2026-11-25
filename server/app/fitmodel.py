"""The trained fit model (Logistic Regression + matching features), ported from the Python notebook.

It reads app/model/model.json (exported from the notebook) and predicts, for one (resume, job) pair,
No Fit / Potential Fit / Good Fit. The maths follows the export exactly:
  1. tokens = lowercase, regex \\b\\w\\w+\\b, drop stop words, keep words in the vocabulary.
  2. tf-idf = token counts x idf, divided by the L2 norm.
  3. features = tfidf(resume + " " + job)  +  num_weight x [similarity, coverage, overlap].
  4. scores = intercept + features . coef, then softmax.
"""
from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from pathlib import Path

import numpy as np

MODEL_PATH = Path(__file__).with_name("model") / "model.json"
LABELS = {0: "No Fit", 1: "Potential Fit", 2: "Good Fit"}


@dataclass
class Prediction:
    label: str
    probabilities: list[float]  # [No Fit, Potential Fit, Good Fit]

    @property
    def score(self) -> int:
        """0-100 ranking score: Good Fit counts fully, Potential Fit half."""
        return round(100 * (self.probabilities[2] + 0.5 * self.probabilities[1]))


class FitModel:
    def __init__(self, data: dict) -> None:
        self.pattern = re.compile(r"\b\w\w+\b")  # token_pattern (?u)\b\w\w+\b
        self.stop = set(data["stop_words"])
        self.vocab: dict[str, int] = data["vocab"]
        self.idf = np.asarray(data["idf"], dtype=np.float64)
        self.num_weight = float(data["num_weight"])
        self.coef = np.asarray(data["coef"], dtype=np.float64)
        self.intercept = np.asarray(data["intercept"], dtype=np.float64)
        self.v = len(self.vocab)
        if self.coef.shape != (3, self.v + 3):
            raise ValueError("model.json: coef must be 3 x (vocab size + 3)")

    @classmethod
    def load(cls, path: Path = MODEL_PATH) -> "FitModel":
        return cls(json.loads(Path(path).read_text(encoding="utf-8")))

    def _tokens(self, text: str) -> list[int]:
        out = []
        for t in self.pattern.findall(text.lower()):
            if t in self.stop:
                continue
            i = self.vocab.get(t)
            if i is not None:
                out.append(i)
        return out

    def _tfidf(self, text: str) -> dict[int, float]:
        counts: dict[int, int] = {}
        for i in self._tokens(text):
            counts[i] = counts.get(i, 0) + 1
        vec = {i: c * self.idf[i] for i, c in counts.items()}
        norm = math.sqrt(sum(x * x for x in vec.values()))
        return {i: x / norm for i, x in vec.items()} if norm > 0 else {}

    def predict(self, resume: str, job: str) -> Prediction:
        both = self._tfidf(resume + " " + job)
        r, j = self._tfidf(resume), self._tfidf(job)
        similarity = sum(x * j[i] for i, x in r.items() if i in j)
        inter = len(r.keys() & j.keys())
        union = len(r.keys() | j.keys())
        coverage = inter / len(j) if j else 0.0
        overlap = inter / union if union else 0.0
        scores = self.intercept.copy()
        if both:
            idx = np.fromiter(both.keys(), dtype=np.int64)
            val = np.fromiter(both.values(), dtype=np.float64)
            scores += self.coef[:, idx] @ val
        scores += self.num_weight * (similarity * self.coef[:, self.v] + coverage * self.coef[:, self.v + 1] + overlap * self.coef[:, self.v + 2])
        e = np.exp(scores - scores.max())
        p = e / e.sum()
        return Prediction(LABELS[int(np.argmax(p))], [round(float(x), 6) for x in p])


_model: FitModel | None = None


def get_model() -> FitModel | None:
    """Loaded once. None if the file is missing, so the app still works (with the rule-based fit)."""
    global _model
    if _model is None:
        try:
            _model = FitModel.load()
        except Exception:  # missing or broken model file
            return None
    return _model

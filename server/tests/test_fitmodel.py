import json
from pathlib import Path

from app.fitmodel import FitModel, get_model


def test_port_matches_the_notebook_exactly():
    model = FitModel.load()
    vectors = json.loads((Path(__file__).parent / "data" / "test_vectors.json").read_text(encoding="utf-8"))
    for v in vectors:
        pred = model.predict(v["resume"], v["job"])
        assert pred.label == v["expected_label"]
        assert max(abs(a - b) for a, b in zip(pred.probabilities, v["expected_probabilities"])) < 0.001


def test_model_is_loaded_by_the_server():
    assert get_model() is not None

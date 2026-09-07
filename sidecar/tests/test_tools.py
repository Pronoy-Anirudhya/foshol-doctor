"""Scoring and seed-stub unit tests."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def _load(name: str, relative: str):
    path = REPO / relative
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def test_eval_scoring_top1_top3_unmapped() -> None:
    eval_mod = _load("foshol_eval", "tools/eval.py")
    mapping = {
        ("m", "v1", "raw_a"): "d_a",
        ("m", "v1", "raw_b"): "d_b",
    }
    rows = [
        {
            "crop_code": "rice",
            "disease_code": "d_a",
            "model_id": "m",
            "model_version": "v1",
            "pred_raw_labels": "raw_a|raw_b",
            "fallback_used": "false",
        },
        {
            "crop_code": "rice",
            "disease_code": "d_a",
            "model_id": "m",
            "model_version": "v1",
            "pred_raw_labels": "raw_b|raw_a",
            "fallback_used": "true",
        },
        {
            "crop_code": "rice",
            "disease_code": "d_a",
            "model_id": "m",
            "model_version": "v1",
            "pred_raw_labels": "raw_unknown",
            "fallback_used": "false",
        },
    ]
    scored = eval_mod.score_predictions(rows, mapping)
    rice = scored["rice"]
    assert rice["n"] == 3
    assert rice["top1"] == 1
    assert rice["top3"] == 2
    assert rice["fallback"] == 1
    assert rice["unmapped"]["raw_unknown"] == 1
    assert rice["confusion"][("d_a", "UNMAPPED")] == 1


def test_seed_count_bounds_and_determinism(tmp_path: Path) -> None:
    seed = _load("foshol_seed", "tools/seed_cases.py")
    assert seed.main(["--count", "10", "--out", str(tmp_path / "x.sql")]) == 1
    first = tmp_path / "a.sql"
    second = tmp_path / "b.sql"
    assert seed.main(["--count", "45", "--seed", "7", "--out", str(first)]) == 0
    assert seed.main(["--count", "45", "--seed", "7", "--out", str(second)]) == 0
    assert first.read_bytes() == second.read_bytes()
    text = first.read_text(encoding="utf-8")
    assert "NULL" in text or "stay NULL" in text
    assert "phone" not in text.lower()
    assert "INSERT INTO farmer" not in text

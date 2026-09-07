#!/usr/bin/env python3
"""Held-out evaluation harness. Writes docs/eval-report.md with this project's metrics only.

Does not invent disease labels. Does not quote third-party accuracy figures.
Scoring maps sidecar raw_label values through a human-supplied label-map CSV
(SIDECAR-DATA-009). If that artefact or the held-out set is absent, the report
records 0 samples and is deferred.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = REPO / "tools" / "eval" / "manifest.csv"
DEFAULT_LABEL_MAP = REPO / "tools" / "eval" / "label-map.csv"
DEFAULT_HELD_OUT = REPO / "tools" / "eval" / "held-out"
DEFAULT_REPORT = REPO / "docs" / "eval-report.md"
MIN_PER_CROP = 100


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _rel(path: Path) -> str:
    try:
        return str(path.relative_to(REPO))
    except ValueError:
        return str(path)


def load_label_map(path: Path) -> dict[tuple[str, str, str], str]:
    mapping: dict[tuple[str, str, str], str] = {}
    if not path.is_file():
        return mapping
    with path.open("r", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            key = (row["model_id"], row["model_version"], row["raw_label"])
            mapping[key] = row["disease_code"]
    return mapping


def score_predictions(
    rows: list[dict[str, str]],
    mapping: dict[tuple[str, str, str], str],
) -> dict:
    """rows: crop_code, disease_code, model_id, model_version, pred_raw_labels (ranked, | separated)."""
    per_crop: dict[str, dict] = defaultdict(
        lambda: {
            "n": 0,
            "top1": 0,
            "top3": 0,
            "confusion": Counter(),
            "unmapped": Counter(),
            "model_ids": set(),
            "model_versions": set(),
            "fallback": 0,
        }
    )
    for row in rows:
        crop = row["crop_code"]
        truth = row["disease_code"]
        model_id = row["model_id"]
        model_version = row["model_version"]
        raw_labels = [item for item in row.get("pred_raw_labels", "").split("|") if item]
        mapped = []
        unmapped = []
        for raw in raw_labels:
            disease = mapping.get((model_id, model_version, raw))
            if disease is None:
                unmapped.append(raw)
                mapped.append(None)
            else:
                mapped.append(disease)
        bucket = per_crop[crop]
        bucket["n"] += 1
        bucket["model_ids"].add(model_id)
        bucket["model_versions"].add(model_version)
        if row.get("fallback_used") == "true":
            bucket["fallback"] += 1
        top1 = mapped[0] if mapped else None
        if top1 is None:
            for raw in unmapped[:1]:
                bucket["unmapped"][raw] += 1
        if top1 == truth:
            bucket["top1"] += 1
        top3 = [item for item in mapped[:3] if item is not None]
        if truth in top3:
            bucket["top3"] += 1
        predicted = top1 or "UNMAPPED"
        bucket["confusion"][(truth, predicted)] += 1
    return per_crop


def write_deferred_report(path: Path, reasons: list[str]) -> None:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        "# Foshol Doctor held-out evaluation",
        "",
        "**Status:** deferred — 0 samples scored.",
        "",
        f"- Run timestamp: `{now}`",
        "- Images scored: 0",
        "- Rice: 0 samples",
        "- Tomato: 0 samples",
        "- Potato: 0 samples",
        "- Mode: not run",
        "- Model id / version: not run",
        "",
        "No accuracy, WER, F1 or precision figure is reported. This file is the only",
        "permitted source of an accuracy number for this project; until a human-labelled",
        "held-out set and label-map artefact exist, there is no accuracy number.",
        "",
        "Reasons:",
        "",
    ]
    for reason in reasons:
        lines.append(f"- {reason}")
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Evaluate a live sidecar against a held-out set.")
    parser.add_argument("--sidecar", default="http://127.0.0.1:8000")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--label-map", type=Path, default=DEFAULT_LABEL_MAP)
    parser.add_argument("--held-out", type=Path, default=DEFAULT_HELD_OUT)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args(argv)

    reasons: list[str] = []
    if not args.manifest.is_file():
        reasons.append(
            f"`{_rel(args.manifest)}` is absent (SIDECAR-FR-095 / C14). "
            "Labels must be human-supplied; this tool will not infer them."
        )
    if not args.label_map.is_file():
        reasons.append(
            f"`{_rel(args.label_map)}` is absent (SIDECAR-DATA-009 / C13). "
            "Blocker for agent A1 — A7 must not author model_label_map rows."
        )

    if reasons:
        write_deferred_report(args.report, reasons)
        print(f"wrote deferred report to {args.report}", file=sys.stderr)
        return 0

    try:
        import urllib.request

        with urllib.request.urlopen(args.sidecar.rstrip("/") + "/health", timeout=5) as resp:
            health = json.loads(resp.read().decode("utf-8"))
    except Exception as exc:  # noqa: BLE001 — report, do not invent metrics
        write_deferred_report(args.report, [f"sidecar unreachable at {args.sidecar}: {exc}"])
        return 1

    if str(health.get("mode", "")).upper() == "REPLAY":
        print("eval.py refuses a REPLAY sidecar (SIDECAR-FR-094)", file=sys.stderr)
        return 1

    write_deferred_report(
        args.report,
        [
            "manifest and label-map files exist, but live scoring is not executed until "
            f"≥{MIN_PER_CROP} labelled images per crop are present.",
        ],
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

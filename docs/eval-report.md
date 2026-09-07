# Foshol Doctor held-out evaluation

**Status:** deferred — 0 samples scored.

- Run timestamp: `2026-09-07T10:31:59Z`
- Images scored: 0
- Rice: 0 samples
- Tomato: 0 samples
- Potato: 0 samples
- Mode: not run
- Model id / version: not run

No accuracy, WER, F1 or precision figure is reported. This file is the only
permitted source of an accuracy number for this project; until a human-labelled
held-out set and label-map artefact exist, there is no accuracy number.

Reasons:

- `tools/eval/manifest.csv` is absent (SIDECAR-FR-095 / C14). Labels must be human-supplied; this tool will not infer them.
- `tools/eval/label-map.csv` is absent (SIDECAR-DATA-009 / C13). Blocker for agent A1 — A7 must not author model_label_map rows.

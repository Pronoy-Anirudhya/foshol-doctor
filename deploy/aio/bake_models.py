"""Pre-fetch exactly the model roles the LIVE + visionary sidecar loads.

Runs in the bake stage, on the BUILD platform, with network access. The Hugging Face
cache and the CTranslate2 int8 model it produces are little-endian serialised tensors
and therefore architecture-independent, so the result is COPYed into the
target-platform runtime stage.

The sidecar's own loaders are called rather than hand-rolled snapshot_download calls:
SentenceTransformer passes its own ignore_patterns, _ensure_ct2_model passes none, and
the vision role uses a single hf_hub_download. Using the same entry points makes the
cache shape correct by construction and fails the build if a model cannot load.

LIVE loads vision.rice.primary, asr.bangla and embed.text (see app/main.py lifespan).
vision.rice.fallback and vision.solanaceae are config-validated and never loaded, so
their weights are deliberately not downloaded.
"""

from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, "/app")

from app.asr import _ensure_ct2_model, load_asr_runtime
from app.config import (
    ROLE_ASR_BANGLA,
    ROLE_EMBED_TEXT,
    ROLE_VISION_RICE_PRIMARY,
    load_settings,
)
from app.embed import load_embed_runtime
from app.live_vision import load_runtime as load_vision_runtime
from huggingface_hub import hf_hub_download

# faster-whisper 1.1.1's WhisperModel falls back to
#   tokenizers.Tokenizer.from_pretrained("openai/whisper-tiny")
# when the model directory has no tokenizer.json. That fetch goes through the Rust
# hf-hub crate, which does NOT honour HF_HUB_OFFLINE, so an offline container cannot
# load ASR at all.
#
# ashrafulparan/whisper-small-bangla ships vocab.json + merges.txt and no
# tokenizer.json, and ctranslate2's converter does not emit one. _ensure_ct2_model
# treats "model.bin AND tokenizer.json" as its skip sentinel (asr.py:418), so that
# sentinel can never be satisfied and every boot re-runs the full int8 conversion with
# force=True.
#
# Baking whisper-tiny's tokenizer.json into ct2/ fixes both: it is the exact file the
# fallback would fetch, and it satisfies the sentinel so the conversion happens once,
# here, at build time.
WHISPER_TINY_REPO = "openai/whisper-tiny"
WHISPER_TINY_REVISION = "169d4a4341b33bc18d8881c4b69c2e104e1cc0af"


def main() -> int:
    settings = load_settings()
    if not settings.is_live:
        raise SystemExit("bake requires FOSHOL_AI_MODE=live")

    print("[bake] vision.rice.primary", flush=True)
    load_vision_runtime(
        settings.models[ROLE_VISION_RICE_PRIMARY],
        settings.torch_threads,
        settings.vision_backend,
    )

    print("[bake] embed.text", flush=True)
    load_embed_runtime(settings.models[ROLE_EMBED_TEXT], settings.torch_threads)

    spec = settings.models[ROLE_ASR_BANGLA]
    if os.environ.get("BAKE_CT2", "1") == "1":
        print("[bake] asr.bangla snapshot + CTranslate2 int8 conversion", flush=True)
        ct2_dir = Path(_ensure_ct2_model(spec))

        print("[bake] planting whisper-tiny tokenizer.json sentinel", flush=True)
        tokenizer = hf_hub_download(
            repo_id=WHISPER_TINY_REPO,
            filename="tokenizer.json",
            revision=WHISPER_TINY_REVISION,
        )
        shutil.copy2(tokenizer, ct2_dir / "tokenizer.json")

        sentinel = ct2_dir / "tokenizer.json"
        if not (ct2_dir / "model.bin").is_file() or not sentinel.is_file():
            raise SystemExit(f"[bake] ct2 sentinel incomplete in {ct2_dir}")

        # Proves the runtime fast path: sentinel satisfied so no reconversion, the
        # tokenizer resolves locally so no network, and the warmup transcription runs.
        print("[bake] asr.bangla load (must not reconvert)", flush=True)
        mtime_before = (ct2_dir / "model.bin").stat().st_mtime
        load_asr_runtime(spec, settings.torch_threads)
        if (ct2_dir / "model.bin").stat().st_mtime != mtime_before:
            raise SystemExit("[bake] model.bin was rewritten: sentinel did not hold")
    else:
        print("[bake] BAKE_CT2=0: snapshot only, first boot will convert", flush=True)
        from huggingface_hub import snapshot_download

        snapshot_download(repo_id=spec.model_id, revision=spec.model_version)

    print("[bake] done", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

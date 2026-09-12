# syntax=docker/dockerfile:1.7
#
# The stock sidecar image plus the model cache, for the four-container Compose stack.
#
# Why this exists: sidecar/Dockerfile ships no weights, so docker-compose.dev.yml boots the
# sidecar with HF_HUB_OFFLINE=0 and downloads ~4.8 GB from Hugging Face on first run. It then
# re-converts Whisper to CTranslate2 int8 on EVERY boot, because _ensure_ct2_model
# (sidecar/app/asr.py:418) gates on a ct2/tokenizer.json that the repo never ships and the
# converter never emits — measured at ~260s per boot.
#
# This variant copies the cache already baked and verified in the all-in-one image, including
# the tokenizer sentinel that stops the re-conversion. The application code is byte-identical
# to the stock image; only the cache and the LIVE defaults are added.
#
# docker-compose.dev.yml mounts a named volume over /home/foshol/.cache/huggingface. Docker
# seeds an EMPTY named volume from the image's directory contents, so the baked weights land
# in the volume on first boot and persist. Set HF_HUB_OFFLINE=1 and TRANSFORMERS_OFFLINE=1 in
# .env so the hub is never contacted.

ARG AIO_IMAGE=foshol-doctor-aio:0.0.1-amd64
ARG SIDECAR_IMAGE=foshol-doctor-sidecar:0.0.1

FROM ${AIO_IMAGE} AS baked

FROM ${SIDECAR_IMAGE}

# HF_HOME is /home/foshol/.cache/huggingface in sidecar/Dockerfile.
COPY --from=baked --chown=foshol:foshol \
     /opt/foshol/hf-cache/hub /home/foshol/.cache/huggingface/hub

# LIVE + visionary defaults so the image is correct even without compose's env block.
# docker-compose.dev.yml sets the same values explicitly and still wins.
ENV FOSHOL_AI_MODE=live \
    FOSHOL_SIDECAR_VISION_BACKEND=visionary \
    FOSHOL_AI_VISION_CROP_ROUTES=rice=rice,tomato=rice,potato=rice,corn=rice,wheat=rice \
    FOSHOL_AI_VISION_RICE_MODEL_ID=VisionaryQuant/5_Crop_Disease_Detection \
    FOSHOL_AI_VISION_RICE_MODEL_REVISION=63080391f7d2bdb331ab356b0d1d9b4b603b3946 \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1

LABEL org.opencontainers.image.title="foshol-doctor-sidecar (weights baked)" \
      org.opencontainers.image.description="LIVE visionary sidecar with the model cache baked in; no Hugging Face access needed at runtime"

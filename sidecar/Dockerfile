FROM python:3.12-slim

WORKDIR /app

RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin foshol \
    && apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir --upgrade pip

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir \
        fastapi==0.115.12 \
        uvicorn==0.34.2 \
        pydantic==2.11.4 \
        python-multipart==0.0.20 \
        pillow==11.2.1 \
        transformers==4.51.3 \
        timm==1.0.15 \
        faster-whisper==1.1.1 \
        ctranslate2==4.5.0 \
        sentence-transformers==3.4.1

COPY --chown=foshol:foshol app /app/app
COPY --chown=foshol:foshol fixtures /app/fixtures

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    FOSHOL_AI_MODE=replay \
    FOSHOL_SIDECAR_VISION_BACKEND=vit \
    FOSHOL_SIDECAR_FIXTURE_DIR=/app/fixtures \
    FOSHOL_AI_VISION_CROP_ROUTES=rice=rice,tomato=solanaceae,potato=solanaceae \
    FOSHOL_AI_VISION_RICE_MODEL_ID=kssrikar4/Rice-Leaf-Disease-Classification \
    FOSHOL_AI_VISION_RICE_MODEL_REVISION=02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26 \
    FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_ID=prithivMLmods/Rice-Leaf-Disease \
    FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_REVISION=170d10e070c308e0e5337690d67371825591f35b \
    FOSHOL_AI_VISION_SOLANACEAE_MODEL_ID=Daksh159/plant-disease-mobilenetv2 \
    FOSHOL_AI_VISION_SOLANACEAE_MODEL_REVISION=d3fb2afc90da83086eff06e9088a889b6c43d4a6 \
    FOSHOL_AI_ASR_MODEL_ID=ashrafulparan/whisper-small-bangla \
    FOSHOL_AI_ASR_MODEL_REVISION=25c88973563146654493b97882fb2806d2fdeaaa \
    FOSHOL_AI_EMBED_MODEL_ID=sentence-transformers/LaBSE \
    FOSHOL_AI_EMBED_MODEL_REVISION=836121a0533e5664b21c7aacc5d22951f2b8b25b \
    HF_HOME=/home/foshol/.cache/huggingface \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1 \
    HF_HUB_DISABLE_TELEMETRY=1

USER foshol
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --retries=120 --start-period=600s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health')"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]

"""FastAPI inference sidecar. Replay serves fixtures; LIVE loads one vision backbone, ASR, and LaBSE."""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.asr import install_live_asr, transcribe_live, transcribe_live_unavailable, transcribe_replay
from app.config import REGISTRY_ROLES, Settings, get_settings
from app.embed import embed_live, embed_live_unavailable, embed_replay, install_live_embed
from app.errors import ERR_SIDECAR_BAD_REQUEST, SidecarError, bad_request, busy, warming_up
from app.live_vision import classify_live, install_live_vision
from app.replay import FixtureStore
from app.schemas import EmbedRequest
from app.vision import classify_live_unavailable, classify_replay, explain_live_unavailable, explain_replay

log = logging.getLogger("foshol.sidecar")


class InferenceGate:
    def __init__(self, permits: int, timeout_ms: int) -> None:
        self._sem = asyncio.Semaphore(permits)
        self._timeout = timeout_ms / 1000.0

    async def acquire(self) -> None:
        try:
            await asyncio.wait_for(self._sem.acquire(), timeout=self._timeout)
        except TimeoutError as exc:
            raise busy() from exc

    def release(self) -> None:
        self._sem.release()


class AppState:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.store = FixtureStore(settings.fixture_dir)
        self.gate = InferenceGate(settings.max_concurrent, settings.queue_timeout_ms)
        self.warm = settings.is_replay
        self.models_loaded = 0
        self.degraded_reasons: list[str] = []
        self.loaded_at: dict[str, str | None] = {role: None for role in REGISTRY_ROLES}
        self.loaded_flags: dict[str, bool] = {role: False for role in REGISTRY_ROLES}
        self.labels: dict[str, list[str]] = {role: [] for role in REGISTRY_ROLES}
        self.vision_runtime = None
        self.classify_cache = None
        self.asr_runtime = None
        self.asr_cache = None
        self.embed_runtime = None
        self.embed_cache = None


def _correlation_id(request: Request) -> str:
    existing = request.headers.get("X-Correlation-Id") or request.headers.get("x-correlation-id")
    if existing and existing.strip():
        return existing.strip()
    return str(uuid.uuid4())


def _state(request: Request) -> AppState:
    return request.app.state.sidecar


def _log_line(
    *,
    correlation_id: str,
    endpoint: str,
    mode: str,
    model_id: str,
    status: int,
    inference_ms: int,
) -> None:
    log.info(
        "timestamp=%s level=INFO correlation_id=%s endpoint=%s mode=%s model_id=%s status=%s inference_ms=%s",
        time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        correlation_id,
        endpoint,
        mode,
        model_id,
        status,
        inference_ms,
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    settings = get_settings()
    sidecar = AppState(settings)
    if settings.is_replay:
        sidecar.warm = True
        sidecar.models_loaded = 0
        log.info(
            "timestamp=%s level=INFO correlation_id=- endpoint=startup mode=REPLAY model_id=- status=200 inference_ms=0",
            time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        )
    else:
        sidecar.warm = False
        install_live_vision(sidecar)
        install_live_asr(sidecar)
        install_live_embed(sidecar)
        sidecar.warm = True
        model_id = getattr(sidecar.vision_runtime, "model_id", "-")
        log.info(
            "timestamp=%s level=INFO correlation_id=- endpoint=startup mode=LIVE model_id=%s status=200 inference_ms=0",
            time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            model_id,
        )
    app.state.sidecar = sidecar
    yield


app = FastAPI(title="Foshol Doctor inference sidecar", lifespan=lifespan)


@app.middleware("http")
async def correlation_middleware(request: Request, call_next):
    correlation_id = _correlation_id(request)
    request.state.correlation_id = correlation_id
    started = time.perf_counter()
    try:
        response = await call_next(request)
    except SidecarError as exc:
        response = _problem_response(request, exc)
    elapsed = max(int((time.perf_counter() - started) * 1000), 0)
    mode = "REPLAY"
    try:
        mode = _state(request).settings.mode_header
    except Exception:
        pass
    response.headers["X-Correlation-Id"] = correlation_id
    response.headers["X-Foshol-Mode"] = mode
    response.headers.setdefault("X-Foshol-Inference-Ms", str(elapsed))
    return response


def _problem_response(request: Request, exc: SidecarError) -> JSONResponse:
    correlation_id = getattr(request.state, "correlation_id", _correlation_id(request))
    body = exc.problem(instance=request.url.path, correlation_id=correlation_id)
    return JSONResponse(
        status_code=exc.status,
        content=body,
        headers=dict(exc.headers),
        media_type="application/problem+json",
    )


@app.exception_handler(SidecarError)
async def sidecar_error_handler(request: Request, exc: SidecarError) -> JSONResponse:
    return _problem_response(request, exc)


@app.exception_handler(RequestValidationError)
async def validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    errors = [
        {"field": ".".join(str(loc) for loc in err.get("loc", [])), "message": err.get("msg", "")}
        for err in exc.errors()
    ]
    return _problem_response(
        request,
        SidecarError(ERR_SIDECAR_BAD_REQUEST, "request validation failed", errors=errors),
    )


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    correlation_id = getattr(request.state, "correlation_id", _correlation_id(request))
    if exc.status_code == 404:
        detail = exc.detail if isinstance(exc.detail, str) else "not found"
        body = {
            "type": "https://foshol.local/problems/not-found",
            "title": "Not found",
            "status": 404,
            "detail": detail,
            "instance": request.url.path,
            "code": ERR_SIDECAR_BAD_REQUEST,
            "correlationId": correlation_id,
        }
        return JSONResponse(status_code=404, content=body, media_type="application/problem+json")
    detail = exc.detail if isinstance(exc.detail, str) else "request failed"
    return _problem_response(request, bad_request(detail))


def _require_warm(state: AppState) -> None:
    if not state.warm:
        raise warming_up()


@asynccontextmanager
async def gated(state: AppState) -> AsyncIterator[None]:
    await state.gate.acquire()
    try:
        yield
    finally:
        state.gate.release()


@app.get("/health")
async def health(request: Request) -> JSONResponse:
    state = _state(request)
    settings = state.settings
    if not state.warm:
        status = "STARTING"
        http_status = 503
    elif settings.is_replay:
        status = "UP"
        http_status = 200
    elif state.models_loaded == 0:
        status = "DOWN"
        http_status = 503
    elif state.degraded_reasons:
        status = "DEGRADED"
        http_status = 200
    else:
        status = "UP"
        http_status = 200
    body = {
        "status": status,
        "mode": settings.mode_header,
        "warm": state.warm,
        "models_loaded": state.models_loaded,
        "models_expected": len(REGISTRY_ROLES),
        "degraded_reasons": list(state.degraded_reasons),
    }
    return JSONResponse(status_code=http_status, content=body)


@app.get("/v1/models")
async def models(request: Request) -> dict[str, Any]:
    state = _state(request)
    settings = state.settings
    if not state.warm:
        raise warming_up()
    views = []
    for role in REGISTRY_ROLES:
        spec = settings.models.get(role)
        loaded = state.loaded_flags.get(role, False)
        views.append(
            {
                "role": role,
                "model_id": spec.model_id if spec else "",
                "model_version": spec.model_version if spec else "",
                "architecture": spec.architecture if spec else "",
                "source": "huggingface",
                "loaded": loaded,
                "warm": state.warm if settings.is_replay else (state.warm and loaded),
                "num_labels": len(state.labels.get(role) or []),
                "loaded_at": state.loaded_at.get(role),
                "labels": list(state.labels.get(role) or []),
            }
        )
    return {
        "mode": settings.mode_header,
        "models": views,
        "inference_ms": 0,
        "correlation_id": request.state.correlation_id,
    }


def _parse_top_k(raw: int | None, default: int) -> int:
    value = default if raw is None else raw
    if value < 1 or value > 20:
        raise bad_request("top_k must be between 1 and 20")
    return value


@app.post("/v1/vision/classify")
async def classify(
    request: Request,
    image: UploadFile = File(...),
    crop_code: str = Form(...),
    top_k: int | None = Form(None),
    model_role: str | None = Form(None),
) -> dict[str, Any]:
    state = _state(request)
    _require_warm(state)
    data = await image.read()
    k = _parse_top_k(top_k, state.settings.vision_top_k)
    async with gated(state):
        if state.settings.is_replay:
            body = classify_replay(
                state.settings,
                state.store,
                data,
                crop_code,
                k,
                model_role,
                request.state.correlation_id,
            )
        else:
            if state.vision_runtime is None or state.classify_cache is None:
                classify_live_unavailable()
                raise AssertionError("unreachable")
            body = classify_live(
                state.settings,
                state.vision_runtime,
                state.classify_cache,
                data,
                crop_code,
                k,
                model_role,
                request.state.correlation_id,
            )
    _log_line(
        correlation_id=request.state.correlation_id,
        endpoint="/v1/vision/classify",
        mode=state.settings.mode_header,
        model_id=str(body.get("model_id") or "-"),
        status=200,
        inference_ms=int(body.get("inference_ms") or 0),
    )
    return body


@app.post("/v1/vision/explain")
async def explain(
    request: Request,
    image: UploadFile = File(...),
    crop_code: str = Form(...),
    model_role: str | None = Form(None),
    target_label: str | None = Form(None),
    alpha: float | None = Form(None),
) -> Response:
    state = _state(request)
    _require_warm(state)
    data = await image.read()
    if alpha is not None and not 0.0 <= alpha <= 1.0:
        raise bad_request("alpha must be between 0.0 and 1.0")
    async with gated(state):
        if state.settings.is_replay:
            png, headers = explain_replay(
                state.settings,
                state.store,
                data,
                crop_code,
                model_role,
                target_label,
            )
        else:
            explain_live_unavailable()
            raise AssertionError("unreachable")
    _log_line(
        correlation_id=request.state.correlation_id,
        endpoint="/v1/vision/explain",
        mode=state.settings.mode_header,
        model_id=headers.get("X-Foshol-Model-Id", "-"),
        status=200,
        inference_ms=0,
    )
    return Response(content=png, media_type="image/png", headers=headers)


@app.post("/v1/asr/transcribe")
async def transcribe(
    request: Request,
    audio: UploadFile = File(...),
    language: str | None = Form(None),
) -> dict[str, Any]:
    state = _state(request)
    _require_warm(state)
    data = await audio.read()
    async with gated(state):
        if state.settings.is_replay:
            body = transcribe_replay(
                state.settings,
                state.store,
                data,
                language,
                request.state.correlation_id,
            )
        else:
            if state.asr_runtime is None or state.asr_cache is None:
                transcribe_live_unavailable()
                raise AssertionError("unreachable")
            body = transcribe_live(
                state.settings,
                state.asr_runtime,
                state.asr_cache,
                data,
                language,
                request.state.correlation_id,
            )
    _log_line(
        correlation_id=request.state.correlation_id,
        endpoint="/v1/asr/transcribe",
        mode=state.settings.mode_header,
        model_id=str(body.get("model_id") or "-"),
        status=200,
        inference_ms=int(body.get("inference_ms") or 0),
    )
    return body


@app.post("/v1/embed")
async def embed(request: Request, payload: EmbedRequest) -> dict[str, Any]:
    state = _state(request)
    _require_warm(state)
    async with gated(state):
        if state.settings.is_replay:
            body = embed_replay(
                state.settings,
                state.store,
                payload.texts,
                request.state.correlation_id,
            )
        else:
            if state.embed_runtime is None or state.embed_cache is None:
                embed_live_unavailable()
                raise AssertionError("unreachable")
            body = embed_live(
                state.settings,
                state.embed_runtime,
                state.embed_cache,
                payload.texts,
                request.state.correlation_id,
            )
    _log_line(
        correlation_id=request.state.correlation_id,
        endpoint="/v1/embed",
        mode=state.settings.mode_header,
        model_id=str(body.get("model_id") or "-"),
        status=200,
        inference_ms=int(body.get("inference_ms") or 0),
    )
    return body

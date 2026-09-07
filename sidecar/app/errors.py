"""Sidecar error codes and RFC 9457 problem documents (SIDECAR-NFR-021)."""

from __future__ import annotations

from typing import Any

ERR_SIDECAR_BAD_REQUEST = "ERR_SIDECAR_BAD_REQUEST"
ERR_SIDECAR_UNKNOWN_CROP = "ERR_SIDECAR_UNKNOWN_CROP"
ERR_SIDECAR_FIXTURE_MISSING = "ERR_SIDECAR_FIXTURE_MISSING"
ERR_SIDECAR_PAYLOAD_TOO_LARGE = "ERR_SIDECAR_PAYLOAD_TOO_LARGE"
ERR_SIDECAR_UNSUPPORTED_MEDIA = "ERR_SIDECAR_UNSUPPORTED_MEDIA"
ERR_SIDECAR_UNDECODABLE = "ERR_SIDECAR_UNDECODABLE"
ERR_SIDECAR_BUSY = "ERR_SIDECAR_BUSY"
ERR_SIDECAR_INFERENCE_FAILED = "ERR_SIDECAR_INFERENCE_FAILED"
ERR_SIDECAR_MODEL_UNAVAILABLE = "ERR_SIDECAR_MODEL_UNAVAILABLE"
ERR_SIDECAR_WARMING_UP = "ERR_SIDECAR_WARMING_UP"
ERR_SIDECAR_EMBED_DIM_MISMATCH = "ERR_SIDECAR_EMBED_DIM_MISMATCH"

STATUS_BY_CODE: dict[str, int] = {
    ERR_SIDECAR_BAD_REQUEST: 400,
    ERR_SIDECAR_UNKNOWN_CROP: 400,
    ERR_SIDECAR_FIXTURE_MISSING: 404,
    ERR_SIDECAR_PAYLOAD_TOO_LARGE: 413,
    ERR_SIDECAR_UNSUPPORTED_MEDIA: 415,
    ERR_SIDECAR_UNDECODABLE: 422,
    ERR_SIDECAR_BUSY: 429,
    ERR_SIDECAR_INFERENCE_FAILED: 500,
    ERR_SIDECAR_MODEL_UNAVAILABLE: 503,
    ERR_SIDECAR_WARMING_UP: 503,
    ERR_SIDECAR_EMBED_DIM_MISMATCH: 500,
}

TITLE_BY_CODE: dict[str, str] = {
    ERR_SIDECAR_BAD_REQUEST: "Bad request",
    ERR_SIDECAR_UNKNOWN_CROP: "Unknown crop_code",
    ERR_SIDECAR_FIXTURE_MISSING: "Replay fixture missing",
    ERR_SIDECAR_PAYLOAD_TOO_LARGE: "Payload too large",
    ERR_SIDECAR_UNSUPPORTED_MEDIA: "Unsupported media type",
    ERR_SIDECAR_UNDECODABLE: "Undecodable payload",
    ERR_SIDECAR_BUSY: "Sidecar busy",
    ERR_SIDECAR_INFERENCE_FAILED: "Inference failed",
    ERR_SIDECAR_MODEL_UNAVAILABLE: "Model unavailable",
    ERR_SIDECAR_WARMING_UP: "Sidecar warming up",
    ERR_SIDECAR_EMBED_DIM_MISMATCH: "Embedding dimension mismatch",
}

SLUG_BY_CODE: dict[str, str] = {
    ERR_SIDECAR_BAD_REQUEST: "sidecar-bad-request",
    ERR_SIDECAR_UNKNOWN_CROP: "sidecar-unknown-crop",
    ERR_SIDECAR_FIXTURE_MISSING: "sidecar-fixture-missing",
    ERR_SIDECAR_PAYLOAD_TOO_LARGE: "sidecar-payload-too-large",
    ERR_SIDECAR_UNSUPPORTED_MEDIA: "sidecar-unsupported-media",
    ERR_SIDECAR_UNDECODABLE: "sidecar-undecodable",
    ERR_SIDECAR_BUSY: "sidecar-busy",
    ERR_SIDECAR_INFERENCE_FAILED: "sidecar-inference-failed",
    ERR_SIDECAR_MODEL_UNAVAILABLE: "sidecar-model-unavailable",
    ERR_SIDECAR_WARMING_UP: "sidecar-warming-up",
    ERR_SIDECAR_EMBED_DIM_MISMATCH: "sidecar-embed-dim-mismatch",
}


class SidecarError(Exception):
    def __init__(
        self,
        code: str,
        detail: str,
        *,
        errors: list[dict[str, str]] | None = None,
        headers: dict[str, str] | None = None,
    ) -> None:
        super().__init__(detail)
        self.code = code
        self.detail = detail
        self.status = STATUS_BY_CODE[code]
        self.title = TITLE_BY_CODE[code]
        self.errors = errors
        self.headers = headers or {}

    def problem(self, *, instance: str, correlation_id: str) -> dict[str, Any]:
        body: dict[str, Any] = {
            "type": f"https://foshol.local/problems/{SLUG_BY_CODE[self.code]}",
            "title": self.title,
            "status": self.status,
            "detail": self.detail,
            "instance": instance,
            "code": self.code,
            "correlationId": correlation_id,
        }
        if self.errors:
            body["errors"] = self.errors
        return body


def bad_request(detail: str, errors: list[dict[str, str]] | None = None) -> SidecarError:
    return SidecarError(ERR_SIDECAR_BAD_REQUEST, detail, errors=errors)


def unknown_crop(crop_code: str) -> SidecarError:
    return SidecarError(
        ERR_SIDECAR_UNKNOWN_CROP,
        f"crop_code {crop_code!r} has no route",
    )


def fixture_missing(digest: str) -> SidecarError:
    return SidecarError(
        ERR_SIDECAR_FIXTURE_MISSING,
        f"no replay fixture for digest {digest}",
    )


def payload_too_large(detail: str) -> SidecarError:
    return SidecarError(ERR_SIDECAR_PAYLOAD_TOO_LARGE, detail)


def unsupported_media(detail: str) -> SidecarError:
    return SidecarError(ERR_SIDECAR_UNSUPPORTED_MEDIA, detail)


def undecodable(detail: str) -> SidecarError:
    return SidecarError(ERR_SIDECAR_UNDECODABLE, detail)


def busy() -> SidecarError:
    return SidecarError(
        ERR_SIDECAR_BUSY,
        "no inference permit within the queue timeout",
        headers={"Retry-After": "1"},
    )


def inference_failed(detail: str) -> SidecarError:
    return SidecarError(ERR_SIDECAR_INFERENCE_FAILED, detail)


def model_unavailable(detail: str) -> SidecarError:
    return SidecarError(ERR_SIDECAR_MODEL_UNAVAILABLE, detail)


def warming_up() -> SidecarError:
    return SidecarError(ERR_SIDECAR_WARMING_UP, "start-up warm-up is incomplete")

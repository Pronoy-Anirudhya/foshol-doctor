"""Bangla ASR: replay fixtures, live faster-whisper, confidence formula."""

from __future__ import annotations

import math
import shutil
import struct
import subprocess
import time
import wave
from collections import OrderedDict
from io import BytesIO
from pathlib import Path
from typing import Any, Protocol

from app.config import ROLE_ASR_BANGLA, ModelSpec, Settings
from app.errors import (
    SidecarError,
    fixture_missing,
    inference_failed,
    model_unavailable,
    payload_too_large,
    undecodable,
    unsupported_media,
)
from app.replay import FixtureStore, sha256_hex
from app.sniff import ALLOWED_AUDIO_TYPES, sniff_audio, wav_duration_seconds

CACHE_MAX = 64
TARGET_HZ = 16000
PEAK_CEILING = 10 ** (-1.0 / 20.0)


def asr_confidence(segments: list[dict[str, Any]]) -> float:
    """Duration-weighted exp(mean logprob), clamped to [0,1], three decimal places."""
    if not segments:
        return 0.0
    weighted = 0.0
    total = 0.0
    for segment in segments:
        duration = max(int(segment["end_ms"]) - int(segment["start_ms"]), 0)
        weighted += float(segment["avg_logprob"]) * duration
        total += duration
    if total <= 0:
        return 0.0
    value = math.exp(weighted / total)
    return round(min(max(value, 0.0), 1.0), 3)


def read_audio(settings: Settings, data: bytes) -> str:
    if len(data) > settings.max_audio_bytes:
        raise payload_too_large("audio exceeds FOSHOL_SIDECAR_MAX_AUDIO_BYTES")
    media = sniff_audio(data)
    if media is None or media not in ALLOWED_AUDIO_TYPES:
        raise unsupported_media("sniffed audio type is not allowed")
    duration = wav_duration_seconds(data)
    if duration is not None and duration > settings.max_audio_seconds:
        raise payload_too_large("audio exceeds FOSHOL_SIDECAR_MAX_AUDIO_SECONDS")
    if media == "audio/wav" and duration is None:
        raise undecodable("WAV bytes could not be decoded")
    return media


def transcribe_replay(
    settings: Settings,
    store: FixtureStore,
    audio: bytes,
    language: str | None,
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    read_audio(settings, audio)
    spec = settings.models[ROLE_ASR_BANGLA]
    digest = sha256_hex(audio)
    body = store.load_json(store.transcribe_path(digest))
    if body is None:
        raise fixture_missing(digest)
    segments = list(body.get("segments") or [])
    speech = bool(body.get("speech_detected", bool(segments)))
    transcript = str(body.get("transcript") or "")
    if not speech:
        transcript = ""
        confidence = 0.0
    elif "confidence" in body:
        confidence = round(float(body["confidence"]), 3)
    else:
        confidence = asr_confidence(segments)
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "REPLAY",
        "model_id": spec.model_id,
        "model_version": spec.model_version,
        "audio_sha256": digest,
        "language": language or settings.asr_language,
        "duration_ms": int(body.get("duration_ms") or 0),
        "sample_rate_hz": int(body.get("sample_rate_hz") or 16000),
        "speech_detected": speech,
        "transcript": transcript,
        "confidence": confidence,
        "segments": segments,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def transcribe_live_unavailable() -> None:
    raise model_unavailable("asr weights are not loaded")


class AsrRuntime(Protocol):
    model_id: str
    model_version: str

    def transcribe(self, samples: list[float], language: str) -> list[dict[str, Any]]:
        ...


class TranscribeCache:
    def __init__(self, max_size: int = CACHE_MAX) -> None:
        self._max = max_size
        self._hits: OrderedDict[str, dict[str, Any]] = OrderedDict()

    def get(self, digest: str) -> dict[str, Any] | None:
        if digest not in self._hits:
            return None
        self._hits.move_to_end(digest)
        return dict(self._hits[digest])

    def put(self, digest: str, payload: dict[str, Any]) -> None:
        self._hits[digest] = dict(payload)
        self._hits.move_to_end(digest)
        while len(self._hits) > self._max:
            self._hits.popitem(last=False)


class FasterWhisperRuntime:
    def __init__(self, model_id: str, model_version: str, model: Any) -> None:
        self.model_id = model_id
        self.model_version = model_version
        self.model = model

    def transcribe(self, samples: list[float], language: str) -> list[dict[str, Any]]:
        import numpy as np

        audio = np.asarray(samples, dtype=np.float32)
        try:
            segments_iter, _info = self.model.transcribe(
                audio,
                language=language,
                beam_size=1,
                vad_filter=False,
            )
            out: list[dict[str, Any]] = []
            for segment in segments_iter:
                out.append(
                    {
                        "start_ms": int(round(float(segment.start) * 1000)),
                        "end_ms": int(round(float(segment.end) * 1000)),
                        "avg_logprob": float(segment.avg_logprob),
                        "no_speech_prob": float(segment.no_speech_prob),
                        "text": str(segment.text or ""),
                    }
                )
            return out
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("ASR forward pass failed") from exc


def _resample_linear(samples: list[float], src_rate: int, dst_rate: int) -> list[float]:
    if src_rate == dst_rate or not samples:
        return samples
    out_len = max(int(round(len(samples) * dst_rate / src_rate)), 1)
    if out_len == 1:
        return [samples[0]]
    result: list[float] = []
    last = len(samples) - 1
    scale = last / (out_len - 1)
    for index in range(out_len):
        src_index = index * scale
        left = int(src_index)
        right = min(left + 1, last)
        frac = src_index - left
        result.append(samples[left] * (1.0 - frac) + samples[right] * frac)
    return result


def _decode_wav_pcm(data: bytes) -> list[float]:
    try:
        with wave.open(BytesIO(data), "rb") as handle:
            channels = handle.getnchannels()
            width = handle.getsampwidth()
            rate = handle.getframerate()
            raw = handle.readframes(handle.getnframes())
    except (wave.Error, EOFError) as exc:
        raise undecodable("WAV bytes could not be decoded") from exc
    if channels < 1 or width not in (1, 2) or rate < 1:
        raise undecodable("WAV bytes could not be decoded")
    if width == 2:
        count = len(raw) // 2
        ints = struct.unpack("<" + "h" * count, raw)
        scale = 32768.0
    else:
        ints = [value - 128 for value in struct.unpack("B" * len(raw), raw)]
        scale = 128.0
    if channels == 1:
        samples = [value / scale for value in ints]
    else:
        samples = []
        for index in range(0, len(ints) - channels + 1, channels):
            acc = 0.0
            for channel in range(channels):
                acc += ints[index + channel]
            samples.append((acc / channels) / scale)
    return _resample_linear(samples, rate, TARGET_HZ)


def _decode_ffmpeg(data: bytes) -> list[float]:
    if shutil.which("ffmpeg") is None:
        raise undecodable("audio bytes could not be decoded")
    try:
        proc = subprocess.run(
            [
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                "pipe:0",
                "-ac",
                "1",
                "-ar",
                str(TARGET_HZ),
                "-f",
                "f32le",
                "-acodec",
                "pcm_f32le",
                "pipe:1",
            ],
            input=data,
            capture_output=True,
            check=False,
            timeout=60,
        )
    except subprocess.TimeoutExpired as exc:
        raise undecodable("audio bytes could not be decoded") from exc
    if proc.returncode != 0 or not proc.stdout:
        raise undecodable("audio bytes could not be decoded")
    count = len(proc.stdout) // 4
    return list(struct.unpack("<" + "f" * count, proc.stdout[: count * 4]))


def _peak_limit(samples: list[float]) -> list[float]:
    peak = 0.0
    for sample in samples:
        magnitude = abs(sample)
        if magnitude > peak:
            peak = magnitude
    if peak <= 0.0:
        return samples
    gain = PEAK_CEILING / peak
    return [sample * gain for sample in samples]


def _ffmpeg_loudness(samples: list[float], target_lufs: float) -> list[float] | None:
    if shutil.which("ffmpeg") is None or not samples:
        return None
    payload = struct.pack("<" + "f" * len(samples), *samples)
    try:
        proc = subprocess.run(
            [
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "f32le",
                "-ar",
                str(TARGET_HZ),
                "-ac",
                "1",
                "-i",
                "pipe:0",
                "-af",
                f"loudnorm=I={target_lufs}:TP=-1.0",
                "-f",
                "f32le",
                "-acodec",
                "pcm_f32le",
                "pipe:1",
            ],
            input=payload,
            capture_output=True,
            check=False,
            timeout=60,
        )
    except subprocess.TimeoutExpired:
        return None
    if proc.returncode != 0 or not proc.stdout:
        return None
    count = len(proc.stdout) // 4
    return list(struct.unpack("<" + "f" * count, proc.stdout[: count * 4]))


def decode_audio_mono_16k(settings: Settings, data: bytes) -> tuple[list[float], int]:
    media = read_audio(settings, data)
    if media == "audio/wav":
        samples = _decode_wav_pcm(data)
    else:
        samples = _decode_ffmpeg(data)
    duration_s = len(samples) / TARGET_HZ if samples else 0.0
    if duration_s > settings.max_audio_seconds:
        raise payload_too_large("audio exceeds FOSHOL_SIDECAR_MAX_AUDIO_SECONDS")
    normalised = _ffmpeg_loudness(samples, settings.asr_target_lufs)
    if normalised is None:
        normalised = _peak_limit(samples)
    duration_ms = int(round(len(normalised) * 1000 / TARGET_HZ)) if normalised else 0
    return normalised, duration_ms


def _public_segments(segments: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "start_ms": int(segment["start_ms"]),
            "end_ms": int(segment["end_ms"]),
            "avg_logprob": float(segment["avg_logprob"]),
            "no_speech_prob": float(segment.get("no_speech_prob") or 0.0),
        }
        for segment in segments
    ]


def _transcript_from_segments(segments: list[dict[str, Any]]) -> tuple[bool, str, float]:
    spoken = [segment for segment in segments if str(segment.get("text") or "").strip()]
    if not spoken:
        return False, "", 0.0
    transcript = " ".join(str(segment["text"]).strip() for segment in spoken)
    confidence = asr_confidence(
        [
            {
                "start_ms": int(segment["start_ms"]),
                "end_ms": int(segment["end_ms"]),
                "avg_logprob": float(segment["avg_logprob"]),
            }
            for segment in spoken
        ]
    )
    return True, transcript, confidence


def transcribe_live(
    settings: Settings,
    runtime: AsrRuntime,
    cache: TranscribeCache,
    audio: bytes,
    language: str | None,
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    samples, duration_ms = decode_audio_mono_16k(settings, audio)
    digest = sha256_hex(audio)
    requested_language = language or settings.asr_language
    cached = cache.get(digest)
    if cached is None:
        try:
            raw_segments = runtime.transcribe(samples, settings.asr_language)
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("ASR forward pass failed") from exc
        speech, transcript, confidence = _transcript_from_segments(raw_segments)
        payload = {
            "speech_detected": speech,
            "transcript": transcript,
            "confidence": confidence,
            "segments": _public_segments(raw_segments),
            "duration_ms": duration_ms,
        }
        cache.put(digest, payload)
    else:
        payload = cached
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "LIVE",
        "model_id": runtime.model_id,
        "model_version": runtime.model_version,
        "audio_sha256": digest,
        "language": requested_language,
        "duration_ms": int(payload["duration_ms"]),
        "sample_rate_hz": TARGET_HZ,
        "speech_detected": bool(payload["speech_detected"]),
        "transcript": str(payload["transcript"]),
        "confidence": float(payload["confidence"]),
        "segments": list(payload["segments"]),
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def _mark_role_loaded(state: Any, role: str) -> None:
    state.loaded_flags[role] = True
    state.loaded_at[role] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    state.models_loaded = sum(1 for flag in state.loaded_flags.values() if flag)
    marker = f"{role} not loaded"
    state.degraded_reasons = [reason for reason in state.degraded_reasons if reason != marker]


def _ensure_ct2_model(spec: ModelSpec) -> Path:
    from huggingface_hub import snapshot_download

    src = Path(snapshot_download(repo_id=spec.model_id, revision=spec.model_version))
    output_dir = src / "ct2"
    if (output_dir / "model.bin").is_file() and (output_dir / "tokenizer.json").is_file():
        return output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    from ctranslate2.converters import TransformersConverter

    converter = TransformersConverter(str(src))
    converter.convert(str(output_dir), quantization="int8", force=True)
    for name in ("tokenizer.json", "preprocessor_config.json"):
        origin = src / name
        target = output_dir / name
        if origin.is_file() and not target.exists():
            shutil.copy2(origin, target)
    return output_dir


def _warmup(runtime: AsrRuntime) -> None:
    runtime.transcribe([0.0] * (TARGET_HZ // 10), "bn")


def load_asr_runtime(spec: ModelSpec, torch_threads: int) -> AsrRuntime:
    from faster_whisper import WhisperModel

    model_path = _ensure_ct2_model(spec)
    model = WhisperModel(
        str(model_path),
        device="cpu",
        compute_type="int8",
        cpu_threads=max(torch_threads, 1),
    )
    runtime = FasterWhisperRuntime(spec.model_id, spec.model_version, model)
    _warmup(runtime)
    return runtime


def install_live_asr(state: Any) -> None:
    settings: Settings = state.settings
    spec = settings.models.get(ROLE_ASR_BANGLA)
    if spec is None:
        raise SystemExit("LIVE mode requires asr.bangla to be configured")
    runtime = load_asr_runtime(spec, settings.torch_threads)
    state.asr_runtime = runtime
    state.asr_cache = TranscribeCache()
    _mark_role_loaded(state, ROLE_ASR_BANGLA)

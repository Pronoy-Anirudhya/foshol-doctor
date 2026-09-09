"""LIVE ASR: mocked faster-whisper runtime, SHA-256 cache. No Hugging Face download."""

from __future__ import annotations

import io
import wave
from dataclasses import dataclass, field

import pytest

from app.asr import TranscribeCache, install_live_asr, transcribe_live
from app.config import reset_settings
from app.errors import ERR_SIDECAR_PAYLOAD_TOO_LARGE, SidecarError
from tests.conftest import wav_bytes

ASR_ID = "ashrafulparan/whisper-small-bangla"
ASR_REV = "25c88973563146654493b97882fb2806d2fdeaaa"


@dataclass
class FakeAsrRuntime:
    model_id: str = ASR_ID
    model_version: str = ASR_REV
    calls: int = 0
    languages: list[str] = field(default_factory=list)
    segments: list[dict] = field(
        default_factory=lambda: [
            {
                "start_ms": 0,
                "end_ms": 800,
                "avg_logprob": -0.2,
                "no_speech_prob": 0.01,
                "text": "pata",
            }
        ]
    )

    def transcribe(self, samples: list[float], language: str) -> list[dict]:
        self.calls += 1
        self.languages.append(language)
        return [dict(row) for row in self.segments]


@pytest.fixture
def live_env(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    reset_settings()
    yield
    reset_settings()


def _stereo_44100_wav(duration_s: float = 0.05) -> bytes:
    frames = int(44100 * duration_s)
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(44100)
        handle.writeframes(b"\x00\x00\x00\x00" * frames)
    return buffer.getvalue()


def test_transcribe_live_reports_model_and_caches(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeAsrRuntime()
    cache = TranscribeCache()
    audio = wav_bytes()
    first = transcribe_live(settings, runtime, cache, audio, None, "corr-1")
    second = transcribe_live(settings, runtime, cache, audio, None, "corr-2")
    assert first["mode"] == "LIVE"
    assert first["model_id"] == ASR_ID
    assert first["model_version"] == ASR_REV
    assert first["language"] == "bn"
    assert first["sample_rate_hz"] == 16000
    assert first["speech_detected"] is True
    assert first["transcript"] == "pata"
    assert first["confidence"] == 0.819
    assert first["audio_sha256"]
    assert runtime.calls == 1
    assert runtime.languages == ["bn"]
    assert second["transcript"] == first["transcript"]
    assert second["correlation_id"] == "corr-2"


def test_transcribe_live_silence_is_empty(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeAsrRuntime(segments=[])
    body = transcribe_live(settings, runtime, TranscribeCache(), wav_bytes(), None, "corr-s")
    assert body["speech_detected"] is False
    assert body["transcript"] == ""
    assert body["confidence"] == 0.0


def test_transcribe_live_never_autodetects_language(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeAsrRuntime()
    transcribe_live(settings, runtime, TranscribeCache(), wav_bytes(), "en", "corr-l")
    assert runtime.languages == ["bn"]


def test_transcribe_live_stereo_44100_is_16k(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeAsrRuntime()
    body = transcribe_live(
        settings, runtime, TranscribeCache(), _stereo_44100_wav(), None, "corr-sr"
    )
    assert body["sample_rate_hz"] == 16000
    assert body["duration_ms"] > 0


def test_transcribe_live_overlength_is_413(live_env):
    from app.config import get_settings

    settings = get_settings()
    audio = wav_bytes(duration_s=31)
    with pytest.raises(SidecarError) as raised:
        transcribe_live(settings, FakeAsrRuntime(), TranscribeCache(), audio, None, "corr-x")
    assert raised.value.code == ERR_SIDECAR_PAYLOAD_TOO_LARGE
    assert raised.value.status == 413


def test_install_live_asr_marks_loaded(live_env, monkeypatch):
    from app.config import REGISTRY_ROLES, get_settings

    fake = FakeAsrRuntime()
    monkeypatch.setattr("app.asr.load_asr_runtime", lambda spec, threads: fake)

    class State:
        def __init__(self):
            self.settings = get_settings()
            self.loaded_flags = {role: False for role in REGISTRY_ROLES}
            self.loaded_flags["vision.rice.primary"] = True
            self.loaded_at = {role: None for role in REGISTRY_ROLES}
            self.labels = {role: [] for role in REGISTRY_ROLES}
            self.models_loaded = 1
            self.degraded_reasons = [
                "vision.rice.fallback not loaded",
                "vision.solanaceae not loaded",
                "asr.bangla not loaded",
                "embed.text not loaded",
            ]
            self.asr_runtime = None
            self.asr_cache = None

    state = State()
    install_live_asr(state)
    assert state.asr_runtime is fake
    assert state.loaded_flags["asr.bangla"] is True
    assert state.loaded_at["asr.bangla"]
    assert state.models_loaded == 2
    assert "asr.bangla not loaded" not in state.degraded_reasons
    assert "embed.text not loaded" in state.degraded_reasons

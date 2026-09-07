"""Magic-byte content-type sniffing (COMMON-SEC-014). Filename and headers are ignored."""

from __future__ import annotations

ALLOWED_IMAGE_TYPES = ("image/jpeg", "image/png", "image/webp")
ALLOWED_AUDIO_TYPES = ("audio/wav", "audio/webm", "audio/ogg", "audio/mp4")


def sniff_image(data: bytes) -> str | None:
    if len(data) >= 8 and data[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if len(data) >= 3 and data[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return None


def sniff_audio(data: bytes) -> str | None:
    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WAVE":
        return "audio/wav"
    if len(data) >= 4 and data[:4] == b"OggS":
        return "audio/ogg"
    if len(data) >= 4 and data[:4] == b"\x1a\x45\xdf\xa3":
        return "audio/webm"
    if _looks_like_mp4(data):
        return "audio/mp4"
    return None


def _looks_like_mp4(data: bytes) -> bool:
    if len(data) < 12:
        return False
    return data[4:8] == b"ftyp"


def image_decodable(data: bytes, media_type: str) -> bool:
    if media_type == "image/png":
        return b"IHDR" in data[:32] and b"IEND" in data
    if media_type == "image/jpeg":
        return b"\xff\xda" in data or data.endswith(b"\xff\xd9")
    if media_type == "image/webp":
        return len(data) >= 20
    return False


def wav_duration_seconds(data: bytes) -> float | None:
    """Parse PCM WAV duration from the fmt/data chunks. Returns None if not a WAV."""
    if sniff_audio(data) != "audio/wav" or len(data) < 44:
        return None
    try:
        offset = 12
        sample_rate = None
        data_bytes = None
        bits = None
        channels = None
        while offset + 8 <= len(data):
            chunk_id = data[offset : offset + 4]
            chunk_size = int.from_bytes(data[offset + 4 : offset + 8], "little")
            payload = offset + 8
            if chunk_id == b"fmt " and chunk_size >= 16 and payload + 16 <= len(data):
                channels = int.from_bytes(data[payload + 2 : payload + 4], "little")
                sample_rate = int.from_bytes(data[payload + 4 : payload + 8], "little")
                bits = int.from_bytes(data[payload + 14 : payload + 16], "little")
            elif chunk_id == b"data":
                data_bytes = chunk_size
                break
            offset = payload + chunk_size + (chunk_size % 2)
        if not sample_rate or not data_bytes or not bits or not channels:
            return None
        bytes_per_sec = sample_rate * channels * (bits // 8)
        if bytes_per_sec <= 0:
            return None
        return data_bytes / bytes_per_sec
    except Exception:
        return None

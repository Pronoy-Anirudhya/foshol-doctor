"""Bangla text normalisation for embeddings (COMMON-NFR-013 / SIDECAR-FR-051)."""

from __future__ import annotations

import unicodedata


def normalise_for_embed(raw: str) -> str:
    nfc = unicodedata.normalize("NFC", raw)
    stripped = nfc.replace("\u200c", "").replace("\u200d", "")
    return fold_bengali_digits(stripped)


def fold_bengali_digits(text: str) -> str:
    out: list[str] = []
    for ch in text:
        code = ord(ch)
        if 0x09E6 <= code <= 0x09EF:
            out.append(chr(ord("0") + (code - 0x09E6)))
        else:
            out.append(ch)
    return "".join(out)

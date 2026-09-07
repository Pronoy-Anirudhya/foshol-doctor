#!/usr/bin/env python3
"""Emit DEMO knowledge-base Flyway SQL and demo capture assets.

Content is labelled DEMO ONLY. It is compiled from publicly described BRRI / IRRI /
BARI / DAE / FAO guidance so the officer console and matcher have rows to show.
It is not production agronomic advice.
"""

from __future__ import annotations

import hashlib
import json
import math
import struct
import unicodedata
import wave
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SQL_OUT = REPO / "app/src/main/resources/db/migration/V18__demo_knowledge_base.sql"
DEMO_IMAGES = REPO / "docs/demo/images"
DEMO_AUDIO = REPO / "docs/demo/audio"
VISION_DIR = REPO / "modules/analysis/src/main/resources/fixtures/vision"
GRADCAM_DIR = REPO / "modules/analysis/src/main/resources/fixtures/gradcam"
ASR_DIR = REPO / "modules/analysis/src/main/resources/fixtures/asr"
EMBED_DIR = REPO / "modules/analysis/src/main/resources/fixtures/embed"
SIDECAR_CLASSIFY = REPO / "sidecar/fixtures/vision/classify"
SIDECAR_EXPLAIN = REPO / "sidecar/fixtures/vision/explain"
SIDECAR_ASR = REPO / "sidecar/fixtures/asr/transcribe"
SIDECAR_INDEX = REPO / "sidecar/fixtures/index.json"
EXISTING_GRADCAM = GRADCAM_DIR / "8b3b8b23ce56af5cc3f864cf5fbca0f46b786901ec423237692c396cee9d1599.png"

RICE = "01800000-0000-7000-8000-000000000001"
TOMATO = "01800000-0000-7000-8000-000000000002"
POTATO = "01800000-0000-7000-8000-000000000003"

D = {
    "rice_brown_spot": "01800000-0000-7000-8000-000000000101",
    "rice_leaf_scald": "01800000-0000-7000-8000-000000000102",
    "rice_blast": "01800000-0000-7000-8000-000000000103",
    "rice_tungro": "01800000-0000-7000-8000-000000000104",
    "rice_sheath_blight": "01800000-0000-7000-8000-000000000105",
    "rice_healthy": "01800000-0000-7000-8000-000000000106",
    "tomato_early_blight": "01800000-0000-7000-8000-000000000107",
    "tomato_late_blight": "01800000-0000-7000-8000-000000000108",
    "tomato_leaf_curl": "01800000-0000-7000-8000-000000000109",
    "tomato_septoria": "01800000-0000-7000-8000-000000000110",
    "tomato_healthy": "01800000-0000-7000-8000-000000000111",
    "potato_early_blight": "01800000-0000-7000-8000-000000000112",
    "potato_late_blight": "01800000-0000-7000-8000-000000000113",
    "potato_healthy": "01800000-0000-7000-8000-000000000114",
}

RICE_MODEL = "kssrikar4/Rice-Leaf-Disease-Classification"
RICE_REV = "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26"
RICE_FALLBACK = "prithivMLmods/Rice-Leaf-Disease"
RICE_FALLBACK_REV = "170d10e070c308e0e5337690d67371825591f35b"
SOL_MODEL = "Daksh159/plant-disease-mobilenetv2"
SOL_REV = "d3fb2afc90da83086eff06e9088a889b6c43d4a6"
ASR_MODEL = "ashrafulparan/whisper-small-bangla"
ASR_REV = "25c88973563146654493b97882fb2806d2fdeaaa"
EMBED_MODEL = "sentence-transformers/LaBSE"

SECONDARY_TRANSCRIPT = "ধানের পাতায় বাদামি গোল দাগ দেখা যাচ্ছে আর পাতা হলুদ হয়ে যাচ্ছে"


def uid(n: int) -> str:
    return f"01800000-0000-7000-8000-{n:012d}"


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def bangla_normalise(raw: str) -> str:
    nfc = unicodedata.normalize("NFC", raw)
    out: list[str] = []
    for ch in nfc:
        cp = ord(ch)
        if cp in (0x200C, 0x200D):
            continue
        if 0x09E6 <= cp <= 0x09EF:
            out.append(chr(ord("0") + (cp - 0x09E6)))
            continue
        cat = unicodedata.category(ch)
        if cat in {"Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po", "Sm", "Sc", "Sk", "So"}:
            out.append(" ")
            continue
        out.append(ch)
    lowered = "".join(out).lower()
    return " ".join(lowered.split())


def symptoms() -> list[tuple[int, str, str, str, str]]:
    # id-suffix, code, name_bn, name_en, organ
    return [
        (301, "leaf_brown_lesions", "পাতায় বাদামি দাগ", "Brown leaf lesions", "LEAF"),
        (302, "leaf_diamond_blast", "হীরকাকৃতি ব্লাস্ট ক্ষত", "Diamond blast lesions", "LEAF"),
        (303, "leaf_scald_bands", "পাতার কিনারা ঝলসানো", "Leaf-margin scald bands", "LEAF"),
        (304, "leaf_yellow_orange", "পাতা হলুদ কমলা", "Yellow-orange leaf discoloration", "LEAF"),
        (305, "sheath_lesions", "খোলে দাগ", "Sheath lesions", "STEM"),
        (306, "panicle_neck_rot", "শীষের গোড়া পচা", "Panicle neck rot", "PANICLE"),
        (307, "stunted_plants", "গাছ বামন", "Stunted plants", "WHOLE"),
        (308, "green_leafhopper", "সবুজ পাতাফড়িং", "Green leafhopper", "WHOLE"),
        (309, "concentric_rings", "গোলকধাঁধা দাগ", "Concentric target spots", "LEAF"),
        (310, "water_soaked_lesions", "পানিভেজা ক্ষত", "Water-soaked lesions", "LEAF"),
        (311, "leaf_curl", "পাতা কুঁকড়ানো", "Leaf curl", "LEAF"),
        (312, "septoria_specks", "ছোট ধূসর দাগ", "Grey specks with dark rims", "LEAF"),
        (313, "fruit_lesions", "ফলে দাগ", "Fruit lesions", "FRUIT"),
        (314, "tuber_rot", "কন্দ পচা", "Tuber rot", "TUBER"),
        (315, "whitefly_colonies", "সাদামাছির কলোনি", "Whitefly colonies", "LEAF"),
    ]


def phrases() -> list[tuple[int, int, str]]:
    # phrase-id, symptom-id, phrase_bn
    rows: list[tuple[int, int, str]] = []
    grouped = {
        301: [
            "ধানের পাতায় বাদামি গোল দাগ দেখা যাচ্ছে",
            "পাতায় ছোট ছোট বাদামি দাগ পড়েছে",
            "পাতার উপর মরচে রঙের দাগ",
            "পাতা বাদামি হয়ে দাগ দাগ হচ্ছে",
            "পাতায় গোল গোল বাদামি চিহ্ন",
            "পাতার মাঝে বাদামি দাগ বাড়ছে",
        ],
        302: [
            "পাতায় হীরক আকৃতির ধূসর ক্ষত",
            "পাতায় চোখের মতো দাগ পড়েছে",
            "ব্লাস্টের মতো ধূসর কেন্দ্রের দাগ",
            "পাতায় সরু হীরক দাগ দেখা যাচ্ছে",
            "পাতার ক্ষত মাঝখানে ধূসর বাইরে বাদামি",
            "ধানের পাতায় ব্লাস্ট দাগ",
        ],
        303: [
            "পাতার কিনারা থেকে শুকিয়ে ঝলসাচ্ছে",
            "পাতার কানা পুড়ে যাওয়ার মতো",
            "পাতার ধার সাদা হয়ে শুকনো",
            "পাতার কিনারায় লম্বা ঝলসানো দাগ",
            "পাতা কানা থেকে ভিতরের দিকে শুকাচ্ছে",
            "পাতার কিনারা পুড়ে খসে যাচ্ছে",
        ],
        304: [
            "পাতা হলুদ হয়ে যাচ্ছে",
            "নতুন পাতা কমলা হলুদ দেখাচ্ছে",
            "গাছের পাতা হলুদ কমলা মিশ্র রং",
            "পাতা হলুদ আর গাছ ছোট থাকছে",
            "পাতায় হলুদ আভা ছড়িয়ে পড়ছে",
            "ধানের পাতা হলুদ হয়ে মরে যাচ্ছে",
        ],
        305: [
            "খোলে ধূসর দাগ পড়েছে",
            "নাড়ার খোলে পোড়া দাগ",
            "খোল পচে নরম হয়ে যাচ্ছে",
            "গাছের খোলে সাদা ছাউনি দাগ",
            "খোলের গোড়ায় ক্ষত দেখা যাচ্ছে",
            "খোল পুড়ে গাছ হেলে পড়ছে",
        ],
        306: [
            "শীষের গোড়া ভেঙে পড়ছে",
            "ধানের শীষ কালো হয়ে ঝুলে আছে",
            "শীষের গলা পচে গেছে",
            "ফুল আসার পর শীষ মরে যাচ্ছে",
            "শীষের গোড়ায় কালো ক্ষত",
            "ধানের নেক ব্লাস্ট হয়েছে",
        ],
        307: [
            "গাছ বামন হয়ে আছে",
            "চারা বাড়ছে না ছোট থাকছে",
            "গাছ খর্বাকৃতির মতো",
            "জমির গাছগুলো সমান বাড়ছে না",
            "গাছ খাটো আর পাতা কম",
            "চারা স্তব্ধ হয়ে গেছে",
        ],
        308: [
            "পাতায় সবুজ পাতাফড়িং দেখা যাচ্ছে",
            "ফড়িং পাতা চুষে খাচ্ছে",
            "সবুজ ফড়িং অনেক জমিতে",
            "পাতাফড়িং উড়ে বেড়াচ্ছে",
            "ধানের পাতায় সবুজ পোকা",
            "পাতাফড়িংয়ের উপদ্রব হয়েছে",
        ],
        309: [
            "পাতায় গোলকধাঁধার মতো দাগ",
            "দাগের ভিতরে বৃত্ত বৃত্ত রেখা",
            "পাতায় টার্গেট দাগ পড়েছে",
            "আলু টমেটোর পাতায় গোল দাগ",
            "পাতায় বাদামি বৃত্তাকার ক্ষত",
            "পাতার দাগ পেঁয়াজ কাটার মতো",
        ],
        310: [
            "পাতায় পানিভেজা দাগ পড়েছে",
            "পাতা ভেজা ভেজা হয়ে পচছে",
            "সকালে পাতায় তৈলাক্ত ক্ষত",
            "পাতা দ্রুত কালো হয়ে মরছে",
            "পাতায় ধূসর পানিভেজা ছোপ",
            "নাবী ধ্বসার মতো ভেজা দাগ",
        ],
        311: [
            "পাতা উপরের দিকে কুঁকড়ে গেছে",
            "নতুন পাতা কুঁকড়ে ছোট",
            "পাতা ভাইরাসের মতো কুঁকড়ানো",
            "চারা পাতা উল্টো কুঁকড়ে আছে",
            "টমেটোর পাতা কুঁকড়ে শিরা মোটা",
            "পাতা কুঁকড়ে গাছ বামন",
        ],
        312: [
            "পাতায় ছোট ধূসর দাগ কালো কিনারা",
            "নিচের পাতায় অসংখ্য ছোট দাগ",
            "দাগের মাঝে ছোট কালো বিন্দু",
            "পাতায় সেপ্টোরিয়ার মতো দাগ",
            "পাতা দাগ দাগ হয়ে ঝরে যাচ্ছে",
            "ছোট গোল দাগে পাতা ছেঁড়া",
        ],
        313: [
            "টমেটোর ফলে দাগ পড়েছে",
            "ফলের গায়ে বাদামি ক্ষত",
            "কাঁচা ফল পচে যাচ্ছে",
            "ফলে কালো দাগ ছড়িয়ে পড়ছে",
            "ফলের তলায় পচা দাগ",
            "টমেটো ফল দাগযুক্ত",
        ],
        314: [
            "আলুর কন্দ পচে যাচ্ছে",
            "কন্দের ভিতর বাদামি পচা",
            "আলু তুললে নরম পচা গন্ধ",
            "কন্দের গায়ে গর্ত পচা",
            "সংরক্ষিত আলু পচে যাচ্ছে",
            "কন্দ কেটলে ভিতর কালো",
        ],
        315: [
            "পাতার নিচে সাদামাছি বসে আছে",
            "পাতা নাড়ালে সাদামাছি উড়ে",
            "সাদামাছির কলোনি পাতায়",
            "পাতায় মধুরস আর সাদা পোকা",
            "টমেটো পাতায় সাদামাছি",
            "সাদামাছি চুষে পাতা হলুদ",
        ],
    }
    pid = 401
    for sid, texts in grouped.items():
        for text in texts:
            rows.append((pid, sid, text))
            pid += 1
    return rows


def json_steps(steps: list[str]) -> str:
    return "$bn$" + json.dumps(steps, ensure_ascii=False) + "$bn$"


def remedies() -> list[dict]:
    demo = (
        "DEMO ONLY — not production advice. Compiled for the Foshol Doctor demo from "
        "BRRI Bangladesh Rice Journal 25(1) 2021 doi:10.3329/brj.v25i1.55177; "
        "IRRI Rice Knowledge Bank disease factsheets; BARI Krishi Projukti Hatboi; "
        "DAE Bangladesh pest-management notes; FAO potato late-blight IPM guidance."
    )
    disclaimer = "ডেমো উপদেশ — মাঠে প্রয়োগের আগে মাঠ কর্মকর্তার অনুমোদন আবশ্যক।"
    rows: list[dict] = []

    def add(
        rid: int,
        disease: str,
        rtype: str,
        title: str,
        steps: list[str],
        dosage: str | None,
        phi: int | None,
        cost: str,
        efficacy: str,
        order: int,
    ) -> None:
        rows.append(
            {
                "id": uid(rid),
                "disease_id": D[disease],
                "type": rtype,
                "title_bn": title,
                "steps_bn": [disclaimer] + steps,
                "dosage_bn": dosage,
                "phi_days": phi,
                "cost_tier": cost,
                "efficacy": efficacy,
                "source_ref": demo,
                "display_order": order,
            }
        )

    # rice brown spot
    add(501, "rice_brown_spot", "CULTURAL", "সারের ভারসাম্য ও বীজ শোধন", [
        "জমি থেকে আক্রান্ত পাতা সরিয়ে পুড়িয়ে ফেলুন।",
        "নাইট্রোজেন কমিয়ে পটাশ ও সিলিকন সমৃদ্ধ সার ধরে রাখুন।",
        "সুস্থ বীজ ব্যবহার করুন এবং ঘন বপন এড়িয়ে চলুন।",
    ], "পটাশ সার মাটি পরীক্ষা অনুযায়ী; অতিরিক্ত ইউরিয়া বন্ধ করুন।", None, "LOW", "MEDIUM", 1)
    add(502, "rice_brown_spot", "ORGANIC", "কচুরীপানা কম্পোস্ট ও ছাই", [
        "জৈব সার ও ধানের ছাই মিশিয়ে জৈব পদার্থ বাড়ান।",
        "বীজ গরম পানিতে শোধন করে শুকিয়ে বুনুন।",
    ], "বীজ ৫৪ ডিগ্রি সেলসিয়াস গরম পানিতে ১০ মিনিট।", None, "LOW", "LOW", 2)
    add(503, "rice_brown_spot", "CHEMICAL", "প্রোপিকোনাজল পাতা স্প্রে", [
        "লক্ষণ দেখা দিলে সকালে পাতার দুই পিঠে স্প্রে করুন।",
        "প্রয়োজনে ১০ থেকে ১৪ দিন পর দ্বিতীয় স্প্রে দিন।",
    ], "Tilt 250 EC ধরনের প্রোপিকোনাজল প্রায় ০.১ শতাংশ দ্রবণ, বিআরআরআই ২০২১।", 21, "MEDIUM", "HIGH", 3)

    add(504, "rice_leaf_scald", "CULTURAL", "ঘনত্ব কমানো ও নাইট্রোজেন নিয়ন্ত্রণ", [
        "সারি থেকে সারি ফাঁক রাখুন যাতে বাতাস চলাচল করে।",
        "অতিরিক্ত ইউরিয়া একবারে দেবেন না।",
        "আক্রান্ত পাতা কেটে সরিয়ে ফেলুন।",
    ], None, None, "LOW", "MEDIUM", 1)
    add(505, "rice_leaf_scald", "BIOLOGICAL", "ট্রাইকোডার্মা বীজ শোধন", [
        "বীজ ট্রাইকোডার্মা গুঁড়ো দিয়ে শোধন করুন।",
        "জমিতে জৈব সার ধরে রাখুন।",
    ], "ট্রাইকোডার্মা হারজিয়ানাম ৪ গ্রাম প্রতি কেজি বীজে।", None, "LOW", "MEDIUM", 2)
    add(506, "rice_leaf_scald", "CHEMICAL", "কার্বেন্ডাজিম স্প্রে", [
        "ঝলসানো কিনারা দেখা দিলে কার্বেন্ডাজিম স্প্রে করুন।",
        "স্প্রে সমানভাবে পাতার কিনারায় পৌঁছান।",
    ], "কার্বেন্ডাজিম ৫০ ডব্লিউপি প্রায় ০.১ শতাংশ দ্রবণ।", 14, "MEDIUM", "MEDIUM", 3)

    add(507, "rice_blast", "CULTURAL", "প্রতিরোধী জাত ও নাইট্রোজেন ভাগ করে দেওয়া", [
        "স্থানীয়ভাবে সুপারিশকৃত ব্লাস্ট-সহনশীল জাত লাগান।",
        "ইউরিয়া ভাগ করে দিন, বুট পর্যায়ে অতিরিক্ত নাইট্রোজেন এড়িয়ে চলুন।",
        "জমিতে পানি শুকিয়ে আবার দেওয়ার চক্র এড়িয়ে স্থির পানি রাখুন।",
    ], None, None, "LOW", "HIGH", 1)
    add(508, "rice_blast", "ORGANIC", "নিম তেল পাতা স্প্রে", [
        "হালকা আক্রমণে নিম তেল স্প্রে করে পোকা ও ছত্রাক চাপ কমান।",
        "সন্ধ্যায় স্প্রে করুন যাতে পাতা পুড়ে না যায়।",
    ], "নিম তেল ৩ মিলি প্রতি লিটার পানিতে, সাবান কণা মিশিয়ে।", None, "LOW", "LOW", 2)
    add(509, "rice_blast", "CHEMICAL", "ট্রাইসাইক্লাজল বুট পর্যায়ে", [
        "বুট আসার আগে প্রতিরোধমূলক স্প্রে দিন।",
        "শীষ বেরোনোর সময় দ্বিতীয় স্প্রে বিবেচনা করুন।",
    ], "ট্রাইসাইক্লাজল ৭৫ ডব্লিউপি লেবেল অনুযায়ী; বিআরআরআই ব্লাস্ট সুপারিশ ২০২১।", 21, "HIGH", "HIGH", 3)

    add(510, "rice_tungro", "CULTURAL", "আক্রান্ত গাছ তুলে ফেলা ও সময়মতো রোপণ", [
        "হলুদ বামন গাছ তুলে পুঁতে বা পুড়িয়ে ফেলুন।",
        "এলাকার অন্য চাষিদের সঙ্গে একই সময়ে রোপণ করুন।",
        "চারা উৎস ক্ষেতে পাতাফড়িং দেখলে চারা আনবেন না।",
    ], None, None, "LOW", "HIGH", 1)
    add(511, "rice_tungro", "BIOLOGICAL", "পাতাফড়িংয়ের প্রাকৃতিক শত্রু রক্ষা", [
        "ক্ষেতের আইলের ঘাস আগাছা রাখুন যেখানে মাকড়সা থাকে।",
        "অপ্রয়োজনীয় কীটনাশক বন্ধ রাখুন।",
    ], None, None, "LOW", "MEDIUM", 2)
    add(512, "rice_tungro", "CHEMICAL", "সবুজ পাতাফড়িং দমন", [
        "ভেক্টর পাতাফড়িং দেখা দিলে অনুমোদিত কীটনাশক স্প্রে করুন।",
        "ভাইরাসের কোনো নিরাময় নেই — শুধু ভেক্টর কমানো যায়।",
    ], "ইমিডাক্লোপ্রিড ১৭.৮ এসএল লেবেল মাত্রায়, শুধু ভেক্টর দমনে।", 7, "MEDIUM", "MEDIUM", 3)

    add(513, "rice_sheath_blight", "CULTURAL", "সারি ফাঁক ও আইল পরিষ্কার", [
        "ঘন রোপণ এড়িয়ে বাতাস চলাচল রাখুন।",
        "আইলের আগাছা পরিষ্কার রাখুন।",
        "অতিরিক্ত নাইট্রোজেন কমান।",
    ], None, None, "LOW", "HIGH", 1)
    add(514, "rice_sheath_blight", "BIOLOGICAL", "ট্রাইকোডার্মা মাটি প্রয়োগ", [
        "রোপণের আগে ট্রাইকোডার্মা মিশ্রিত কম্পোস্ট মাটিতে মেশান।",
        "খোলের গোড়া শুকনো রাখার চেষ্টা করুন।",
    ], "ট্রাইকোডার্মা ২.৫ কেজি প্রতি হেক্টর কম্পোস্টের সঙ্গে।", None, "LOW", "MEDIUM", 2)
    add(515, "rice_sheath_blight", "CHEMICAL", "প্রোপিকোনাজল খোল স্প্রে", [
        "খোলের গোড়ায় স্প্রে নজল নামিয়ে দিন যাতে ক্ষত ভিজে।",
        "বিআরআরআই সুপারিশ অনুযায়ী হেক্টরপ্রতি মাত্রা ধরুন।",
    ], "Tilt 250 EC প্রায় ১ লিটার প্রতি হেক্টর, বিআরআরআই ২০২১।", 21, "MEDIUM", "HIGH", 3)

    add(516, "tomato_early_blight", "CULTURAL", "ফসল চক্র ও নিচের পাতা তোলা", [
        "একই জমিতে টমেটো-আলু পরপর লাগাবেন না।",
        "মাটি থেকে ছিটকে লাগা নিচের পাতা তুলে ফেলুন।",
        "মালচ দিয়ে মাটির ছিটা কমান।",
    ], None, None, "LOW", "HIGH", 1)
    add(517, "tomato_early_blight", "ORGANIC", "কপার সাবান স্প্রে", [
        "হালকা আক্রমণে কপার সাবান সন্ধ্যায় স্প্রে করুন।",
        "ফল পাকার কাছাকাছি রাসায়নিক কমান।",
    ], "কপার সাবান লেবেল অনুযায়ী পাতার দুই পিঠে।", None, "LOW", "MEDIUM", 2)
    add(518, "tomato_early_blight", "CHEMICAL", "ম্যানকোজেব প্রতিরোধ স্প্রে", [
        "গোলকধাঁধা দাগ দেখা দিলে ৭ দিন অন্তর স্প্রে করুন।",
        "বৃষ্টির পর স্প্রে ধুয়ে গেলে পুনরায় দিন।",
    ], "ম্যানকোজেব ৭৫ ডব্লিউপি প্রায় ০.২ শতাংশ দ্রবণ, বিএআরআই/ডিএই টমেটো দশা।", 7, "MEDIUM", "HIGH", 3)

    add(519, "tomato_late_blight", "CULTURAL", "সকালে পাতা শুকনো রাখা", [
        "সন্ধ্যার পর সেচ দেবেন না।",
        "ঘন গাছ পাতলা করুন।",
        "আক্রান্ত গাছ তুলে পুড়িয়ে ফেলুন, কম্পোস্টে ফেলবেন না।",
    ], None, None, "LOW", "HIGH", 1)
    add(520, "tomato_late_blight", "ORGANIC", "বোর্দো মিশ্রণের ধরনের তামার স্প্রে", [
        "ভেজা আবহাওয়ায় তামার স্প্রে প্রতিরোধমূলক দিন।",
        "ফলন তোলার আগে তামার অবশিষ্টাংশ খেয়াল রাখুন।",
    ], "কপার অক্সিক্লোরাইড ৫০ ডব্লিউপি লেবেল মাত্রায়।", None, "MEDIUM", "MEDIUM", 2)
    add(521, "tomato_late_blight", "CHEMICAL", "মেটালাক্সিল ম্যানকোজেব", [
        "পানিভেজা দাগ দেখা দিলে অবিলম্বে স্প্রে করুন।",
        "একই গ্রুপের ছত্রাকনাশক বারবার একা ব্যবহার করবেন না।",
    ], "মেটালাক্সিল ৮ শতাংশ + ম্যানকোজেব ৬৪ শতাংশ ডব্লিউপি লেবেল অনুযায়ী।", 14, "HIGH", "HIGH", 3)

    add(522, "tomato_leaf_curl", "CULTURAL", "সাদামাছির জাল ও আক্রান্ত চারা তোলা", [
        "নার্সারিতে ৫০ মেশ জাল ব্যবহার করুন।",
        "কুঁকড়ানো চারা তুলে ধ্বংস করুন।",
        "আগাছা পরিষ্কার রাখুন যেখানে সাদামাছি লুকোয়।",
    ], None, None, "LOW", "HIGH", 1)
    add(523, "tomato_leaf_curl", "ORGANIC", "হলুদ স্টিকি ফাঁদ ও নিম", [
        "হলুদ আঠালো ফাঁদ ঝুলিয়ে সাদামাছি নজরদারি করুন।",
        "নিম তেল স্প্রে করে চোষণ কমান।",
    ], "নিম তেল ৩ মিলি প্রতি লিটার, ৭ দিন অন্তর।", None, "LOW", "MEDIUM", 2)
    add(524, "tomato_leaf_curl", "CHEMICAL", "সাদামাছি ভেক্টর দমন", [
        "ভাইরাসের নিরাময় নেই — শুধু সাদামাছি কমান।",
        "ফুল আসার পর অমিত্র কীটনাশক এড়িয়ে চলুন।",
    ], "ইমিডাক্লোপ্রিড ১৭.৮ এসএল লেবেল মাত্রায় চারা পর্যায়ে।", 7, "MEDIUM", "MEDIUM", 3)

    add(525, "tomato_septoria", "CULTURAL", "নিচের পাতা ও ফসল অবশিষ্টাংশ সরানো", [
        "মাটির কাছের দাগযুক্ত পাতা তুলে ফেলুন।",
        "সেচের পানি পাতায় ছিটাবেন না।",
        "মৌসুম শেষে গাছের অবশিষ্টাংশ পুড়িয়ে ফেলুন।",
    ], None, None, "LOW", "HIGH", 1)
    add(526, "tomato_septoria", "ORGANIC", "কম্পোস্ট চা ও পাতা শুকনো রাখা", [
        "সকালে সেচ দিন যাতে পাতা দ্রুত শুকায়।",
        "জৈব মালচ দিন।",
    ], None, None, "LOW", "LOW", 2)
    add(527, "tomato_septoria", "CHEMICAL", "ক্লোরোথ্যালোনিল বা ম্যানকোজেব", [
        "ছোট ধূসর দাগ বাড়লে ৭ থেকে ১০ দিন অন্তর স্প্রে করুন।",
        "স্প্রে পাতার নিচের পিঠে পৌঁছান।",
    ], "ম্যানকোজেব ৭৫ ডব্লিউপি প্রায় ০.২ শতাংশ দ্রবণ।", 7, "MEDIUM", "HIGH", 3)

    add(528, "potato_early_blight", "CULTURAL", "ফসল চক্র ও কন্দ বীজ বাছাই", [
        "সুস্থ বীজ কন্দ লাগান।",
        "টমেটোর পর আলু লাগাবেন না।",
        "গাছের নিচের দাগযুক্ত পাতা সরান।",
    ], None, None, "LOW", "MEDIUM", 1)
    add(529, "potato_early_blight", "ORGANIC", "কপার স্প্রে", [
        "লক্ষণের শুরুতে তামার স্প্রে দিন।",
        "পাতা ভিজিয়ে রাখবেন না।",
    ], "কপার অক্সিক্লোরাইড ৫০ ডব্লিউপি লেবেল অনুযায়ী।", None, "LOW", "MEDIUM", 2)
    add(530, "potato_early_blight", "CHEMICAL", "ম্যানকোজেব আলু পাতা", [
        "গোলকধাঁধা দাগ দেখা দিলে ৭ দিন অন্তর স্প্রে করুন।",
        "কন্দ তোলার আগে ফাইটোস্যানিটারি বিরতি মানুন।",
    ], "ম্যানকোজেব ৭৫ ডব্লিউপি প্রায় ০.২ শতাংশ, বিএআরআই আলু দশা।", 14, "MEDIUM", "HIGH", 3)

    add(531, "potato_late_blight", "CULTURAL", "আবহাওয়া নজর ও আক্রান্ত গাছ তোলা", [
        "ঠান্ডা ভেজা রাতে ক্ষেত ঘুরে দেখুন।",
        "আক্রান্ত গাছ তুলে পুড়িয়ে ফেলুন।",
        "কন্দ তোলার আগে লতা মেরে শুকনো করুন।",
    ], None, None, "LOW", "HIGH", 1)
    add(532, "potato_late_blight", "ORGANIC", "তামার প্রতিরোধ স্প্রে", [
        "পূর্বাভাস ভেজা হলে তামার স্প্রে আগে দিন।",
        "সংরক্ষণাগারে পচা কন্দ আলাদা করুন।",
    ], "কপার অক্সিক্লোরাইড ৫০ ডব্লিউপি লেবেল মাত্রায়।", None, "MEDIUM", "MEDIUM", 2)
    add(533, "potato_late_blight", "CHEMICAL", "মেটালাক্সিল ম্যানকোজেব আলু", [
        "নাবী ধ্বসা সন্দেহ হলে অবিলম্বে মিশ্র ছত্রাকনাশক দিন।",
        "একই ক্রিয়াপদ্ধতি বারবার একা ব্যবহার করবেন না।",
    ], "মেটালাক্সিল + ম্যানকোজেব লেবেল অনুযায়ী; এফএও আলু আইপিএম।", 14, "HIGH", "HIGH", 3)

    return rows


def weights() -> list[tuple[str, int, str]]:
    return [
        (D["rice_brown_spot"], 301, "0.900"),
        (D["rice_brown_spot"], 304, "0.250"),
        (D["rice_brown_spot"], 307, "0.200"),
        (D["rice_leaf_scald"], 303, "0.950"),
        (D["rice_leaf_scald"], 301, "0.300"),
        (D["rice_blast"], 302, "0.900"),
        (D["rice_blast"], 306, "0.700"),
        (D["rice_blast"], 301, "0.250"),
        (D["rice_tungro"], 304, "0.900"),
        (D["rice_tungro"], 308, "0.800"),
        (D["rice_tungro"], 307, "0.700"),
        (D["rice_sheath_blight"], 305, "0.950"),
        (D["rice_sheath_blight"], 307, "0.300"),
        (D["tomato_early_blight"], 309, "0.900"),
        (D["tomato_early_blight"], 313, "0.400"),
        (D["tomato_late_blight"], 310, "0.900"),
        (D["tomato_late_blight"], 313, "0.500"),
        (D["tomato_leaf_curl"], 311, "0.950"),
        (D["tomato_leaf_curl"], 315, "0.800"),
        (D["tomato_leaf_curl"], 307, "0.400"),
        (D["tomato_septoria"], 312, "0.950"),
        (D["tomato_septoria"], 301, "0.200"),
        (D["potato_early_blight"], 309, "0.900"),
        (D["potato_early_blight"], 314, "0.200"),
        (D["potato_late_blight"], 310, "0.900"),
        (D["potato_late_blight"], 314, "0.800"),
    ]


def label_map() -> list[tuple[str, str, str, str]]:
    rows: list[tuple[str, str, str, str]] = []

    def add(model: str, version: str, raw: str, disease: str) -> None:
        rows.append((model, version, raw, D[disease]))

    rice_primary = [
        ("Brown Spot", "rice_brown_spot"),
        ("brown_spot", "rice_brown_spot"),
        ("BrownSpot", "rice_brown_spot"),
        ("Brown spot", "rice_brown_spot"),
        ("Leaf Blast", "rice_blast"),
        ("leaf_blast", "rice_blast"),
        ("Blast", "rice_blast"),
        ("Neck Blast", "rice_blast"),
        ("neck_blast", "rice_blast"),
        ("Leaf Scald", "rice_leaf_scald"),
        ("leaf_scald", "rice_leaf_scald"),
        ("Tungro", "rice_tungro"),
        ("tungro", "rice_tungro"),
        ("Sheath Blight", "rice_sheath_blight"),
        ("sheath_blight", "rice_sheath_blight"),
        ("SheathBlight", "rice_sheath_blight"),
        ("Healthy", "rice_healthy"),
        ("healthy", "rice_healthy"),
    ]
    for raw, disease in rice_primary:
        add(RICE_MODEL, RICE_REV, raw, disease)

    rice_fallback = [
        ("Brown Spot", "rice_brown_spot"),
        ("brown_spot", "rice_brown_spot"),
        ("Leaf Blast", "rice_blast"),
        ("Blast", "rice_blast"),
        ("rice_leaf_blast", "rice_blast"),
        ("Leaf Scald", "rice_leaf_scald"),
        ("leaf_scald", "rice_leaf_scald"),
        ("Healthy", "rice_healthy"),
        ("healthy", "rice_healthy"),
        ("Sheath Blight", "rice_sheath_blight"),
        ("Tungro", "rice_tungro"),
    ]
    for raw, disease in rice_fallback:
        add(RICE_FALLBACK, RICE_FALLBACK_REV, raw, disease)

    sol = [
        ("Tomato___Early_blight", "tomato_early_blight"),
        ("Tomato Early blight", "tomato_early_blight"),
        ("Tomato_Early_blight", "tomato_early_blight"),
        ("Tomato___Late_blight", "tomato_late_blight"),
        ("Tomato Late blight", "tomato_late_blight"),
        ("Tomato_Late_blight", "tomato_late_blight"),
        ("Tomato___Tomato_Yellow_Leaf_Curl_Virus", "tomato_leaf_curl"),
        ("Tomato___Leaf_Curl", "tomato_leaf_curl"),
        ("Tomato Yellow Leaf Curl Virus", "tomato_leaf_curl"),
        ("Tomato___Septoria_leaf_spot", "tomato_septoria"),
        ("Tomato Septoria leaf spot", "tomato_septoria"),
        ("Tomato___healthy", "tomato_healthy"),
        ("Tomato healthy", "tomato_healthy"),
        ("Potato___Early_blight", "potato_early_blight"),
        ("Potato Early blight", "potato_early_blight"),
        ("Potato_Early_blight", "potato_early_blight"),
        ("Potato___Late_blight", "potato_late_blight"),
        ("Potato Late blight", "potato_late_blight"),
        ("Potato_Late_blight", "potato_late_blight"),
        ("Potato___healthy", "potato_healthy"),
        ("Potato healthy", "potato_healthy"),
    ]
    for raw, disease in sol:
        add(SOL_MODEL, SOL_REV, raw, disease)
    return rows


def emit_sql() -> str:
    parts: list[str] = [
        "-- DEMO knowledge base. Not production agronomic advice.",
        "-- Compiled from BRRI (2021), IRRI Rice Knowledge Bank, BARI handbook, DAE notes, FAO IPM.",
        "-- V10/V11 scaffolds keep their checksums; this migration overwrites display text and fills V12–V17 gaps.",
        "",
        "UPDATE crop SET name_bn = 'ধান', updated_by = 'demo-seed' WHERE id = " + sql_str(RICE) + ";",
        "UPDATE crop SET name_bn = 'টমেটো', updated_by = 'demo-seed' WHERE id = " + sql_str(TOMATO) + ";",
        "UPDATE crop SET name_bn = 'আলু', updated_by = 'demo-seed' WHERE id = " + sql_str(POTATO) + ";",
        "",
    ]
    diseases = [
        (D["rice_brown_spot"], "বাদামি দাগ", "Brown spot",
         "পাতায় গোল বাদামি দাগ, মাঝে ধূসর কেন্দ্র। অপুষ্টি ও ভেজা আবহাওয়ায় বাড়ে।", "MODERATE"),
        (D["rice_leaf_scald"], "পাতা ঝলসানো", "Leaf scald",
         "পাতার কিনারা থেকে লম্বা ঝলসানো দাগ ভিতরের দিকে যায়।", "MODERATE"),
        (D["rice_blast"], "ব্লাস্ট", "Blast",
         "পাতায় হীরকাকৃতি ধূসর ক্ষত; শীষের গোড়া পচে ঝুলে পড়তে পারে।", "HIGH"),
        (D["rice_tungro"], "টুংরো", "Tungro",
         "ভাইরাসজনিত বামন ও হলুদ পাতা; সবুজ পাতাফড়িং ছড়ায়। ছত্রাকনাশকে সারে না।", "HIGH"),
        (D["rice_sheath_blight"], "খোলপোড়া", "Sheath blight",
         "খোলে ধূসর-সবুজ দাগ, ঘন জমিতে দ্রুত ছড়ায়, গাছ হেলে পড়তে পারে।", "HIGH"),
        (D["rice_healthy"], "সুস্থ ধান", "Healthy",
         "লক্ষণহীন সবুজ পাতা ও স্বাভাবিক কুশি।", "NONE"),
        (D["tomato_early_blight"], "আগাম ধ্বসা", "Early blight",
         "পাতায় গোলকধাঁধা দাগ; নিচের পাতা আগে আক্রান্ত হয়।", "MODERATE"),
        (D["tomato_late_blight"], "নাবী ধ্বসা", "Late blight",
         "পানিভেজা দাগ দ্রুত কালো হয়; ভেজা ঠান্ডা আবহাওয়ায় ফল ও কাণ্ড পচে।", "CRITICAL"),
        (D["tomato_leaf_curl"], "পাতা কুঁকড়ানো ভাইরাস", "Leaf curl virus",
         "নতুন পাতা উপরের দিকে কুঁকড়ে, শিরা মোটা, গাছ বামন; সাদামাছি ছড়ায়।", "HIGH"),
        (D["tomato_septoria"], "সেপ্টোরিয়া পাতা দাগ", "Septoria leaf spot",
         "ছোট ধূসর দাগ কালো কিনারা, মাঝে কালো বিন্দু; নিচের পাতা ঝরে।", "MODERATE"),
        (D["tomato_healthy"], "সুস্থ টমেটো", "Healthy",
         "লক্ষণহীন সবুজ পাতা ও স্বাভাবিক ফলন।", "NONE"),
        (D["potato_early_blight"], "আলুর আগাম ধ্বসা", "Early blight",
         "পাতায় বৃত্তাকার দাগ; কন্দে শুকনো পচা হতে পারে।", "MODERATE"),
        (D["potato_late_blight"], "আলুর নাবী ধ্বসা", "Late blight",
         "পানিভেজা পাতা দ্রুত মরে; কন্দ বাদামি পচে যায়।", "CRITICAL"),
        (D["potato_healthy"], "সুস্থ আলু", "Healthy",
         "লক্ষণহীন সবুজ গাছ ও শক্ত কন্দ।", "NONE"),
    ]
    for did, name_bn, name_en, desc, severity in diseases:
        parts.append(
            "UPDATE disease SET name_bn = {bn}, name_en = {en}, description_bn = {desc}, "
            "severity = {sev}, updated_by = 'demo-seed' WHERE id = {did};".format(
                bn=sql_str(name_bn),
                en=sql_str(name_en),
                desc=sql_str(desc),
                sev=sql_str(severity),
                did=sql_str(did),
            )
        )
    parts.append("")
    parts.append("DELETE FROM model_label_map;")
    parts.append("DELETE FROM disease_symptom;")
    parts.append("DELETE FROM remedy;")
    parts.append("DELETE FROM symptom_phrase;")
    parts.append("DELETE FROM symptom;")
    parts.append("")
    for sid, code, name_bn, name_en, organ in symptoms():
        parts.append(
            "INSERT INTO symptom (id, code, name_bn, name_en, organ, created_by, updated_by) VALUES ("
            f"{sql_str(uid(sid))}, {sql_str(code)}, {sql_str(name_bn)}, {sql_str(name_en)}, "
            f"{sql_str(organ)}, 'demo-seed', 'demo-seed');"
        )
    parts.append("")
    for pid, sid, phrase in phrases():
        parts.append(
            "INSERT INTO symptom_phrase (id, symptom_id, phrase_bn, normalised_bn, created_by, updated_by) VALUES ("
            f"{sql_str(uid(pid))}, {sql_str(uid(sid))}, {sql_str(phrase)}, {sql_str(bangla_normalise(phrase))}, "
            "'demo-seed', 'demo-seed');"
        )
    parts.append("")
    for disease_id, sid, weight in weights():
        parts.append(
            "INSERT INTO disease_symptom (disease_id, symptom_id, weight) VALUES ("
            f"{sql_str(disease_id)}, {sql_str(uid(sid))}, {weight});"
        )
    parts.append("")
    for row in remedies():
        dosage = "NULL" if row["dosage_bn"] is None else sql_str(row["dosage_bn"])
        phi = "NULL" if row["phi_days"] is None else str(row["phi_days"])
        parts.append(
            "INSERT INTO remedy (id, disease_id, type, title_bn, steps_bn, dosage_bn, phi_days, "
            "cost_tier, efficacy, source_ref, display_order, active, created_by, updated_by) VALUES ("
            f"{sql_str(row['id'])}, {sql_str(row['disease_id'])}, {sql_str(row['type'])}, "
            f"{sql_str(row['title_bn'])}, {json_steps(row['steps_bn'])}::jsonb, {dosage}, {phi}, "
            f"{sql_str(row['cost_tier'])}, {sql_str(row['efficacy'])}, {sql_str(row['source_ref'])}, "
            f"{row['display_order']}, true, 'demo-seed', 'demo-seed');"
        )
    parts.append("")
    parts.extend(
        [
            "CREATE OR REPLACE FUNCTION foshol_demo_embedding(input text) RETURNS vector(768)",
            "LANGUAGE plpgsql IMMUTABLE AS $$",
            "DECLARE",
            "  dims float8[] := ARRAY[]::float8[];",
            "  i int;",
            "  h bytea;",
            "  n float8 := 0;",
            "  v float8;",
            "BEGIN",
            "  FOR i IN 0..191 LOOP",
            "    h := decode(md5(coalesce(input, '') || ':' || i::text), 'hex');",
            "    dims := dims || ARRAY[",
            "      (get_byte(h, 0)::float8 / 127.5) - 1.0,",
            "      (get_byte(h, 1)::float8 / 127.5) - 1.0,",
            "      (get_byte(h, 2)::float8 / 127.5) - 1.0,",
            "      (get_byte(h, 3)::float8 / 127.5) - 1.0",
            "    ];",
            "  END LOOP;",
            "  FOREACH v IN ARRAY dims LOOP",
            "    n := n + v * v;",
            "  END LOOP;",
            "  n := sqrt(GREATEST(n, 1e-12));",
            "  FOR i IN 1..768 LOOP",
            "    dims[i] := dims[i] / n;",
            "  END LOOP;",
            "  RETURN dims::vector(768);",
            "END;",
            "$$;",
            "",
            "UPDATE symptom_phrase SET embedding = foshol_demo_embedding(normalised_bn) WHERE deleted_at IS NULL;",
            "UPDATE symptom SET embedding = foshol_demo_embedding(name_bn) WHERE deleted_at IS NULL;",
            "DROP FUNCTION foshol_demo_embedding(text);",
            "",
        ]
    )
    lid = 601
    for model, version, raw, disease_id in label_map():
        parts.append(
            "INSERT INTO model_label_map (id, model_id, model_version, raw_label, disease_id) VALUES ("
            f"{sql_str(uid(lid))}, {sql_str(model)}, {sql_str(version)}, {sql_str(raw)}, {sql_str(disease_id)});"
        )
        lid += 1
    parts.append("")
    return "\n".join(parts) + "\n"


def png_bytes(width: int, height: int, pixels: bytes) -> bytes:
    raw = b"".join(b"\x00" + pixels[y * width * 3 : (y + 1) * width * 3] for y in range(height))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def leaf_pixels(kind: str, size: int = 512) -> bytes:
    buf = bytearray(size * size * 3)
    for y in range(size):
        for x in range(size):
            cx, cy = x - size / 2, y - size / 2
            rx, ry = 0.38 * size, 0.46 * size
            inside = (cx * cx) / (rx * rx) + (cy * cy) / (ry * ry) <= 1.0
            vein = abs(cx) < 3 and abs(cy) < ry
            i = (y * size + x) * 3
            if not inside:
                buf[i : i + 3] = b"\x3a\x5c\x2a" if (x + y) % 17 == 0 else b"\x2f\x4a\x24"
                continue
            g = 70 + (x * 3 + y * 5) % 40
            buf[i] = 28
            buf[i + 1] = min(g, 140)
            buf[i + 2] = 32
            if vein:
                buf[i : i + 3] = bytes((40, 90, 40))
            spot = False
            if kind == "blast":
                spot = ((x // 18 + y // 14) % 5 == 0) and ((x + y) % 11 < 4)
                if spot:
                    buf[i : i + 3] = bytes((90, 90, 70))
            elif kind == "brown":
                spot = ((x - 40) ** 2 + (y - 60) ** 2) % 2200 < 180
                if spot:
                    buf[i : i + 3] = bytes((120, 70, 30))
            elif kind == "ambiguous":
                if (x + y) % 23 < 3:
                    buf[i : i + 3] = bytes((110, 75, 40))
                if (x * 3 + y) % 41 < 2:
                    buf[i : i + 3] = bytes((95, 95, 75))
            elif kind == "early":
                r2 = (x - size * 0.4) ** 2 + (y - size * 0.45) ** 2
                if 400 < r2 < 2200 or 5000 < r2 < 7000:
                    buf[i : i + 3] = bytes((130, 80, 35))
            elif kind == "late":
                if (x // 16 + y // 16) % 3 == 0:
                    buf[i : i + 3] = bytes((40, 50, 40))
            elif kind == "healthy":
                pass
            elif kind == "blur":
                # Near-uniform field so Laplacian variance stays below foshol.intake.quality.blur-variance-min.
                buf[i] = 72
                buf[i + 1] = 96
                buf[i + 2] = 64
    return bytes(buf)


def write_wav(path: Path, seconds: float = 1.2) -> None:
    rate = 16000
    n = int(rate * seconds)
    with wave.open(str(path), "w") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        frames = bytearray()
        for i in range(n):
            # Quiet non-zero tone so the file is not a digital-silence fixture.
            sample = int(800 * math.sin(2 * math.pi * 220 * i / rate))
            frames.extend(struct.pack("<h", sample))
        handle.writeframes(frames)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_vision_fixture(sha: str, crop: str, top: str, conf: float, second: str, second_conf: float) -> None:
    model = RICE_MODEL if crop == "rice" else SOL_MODEL
    version = RICE_REV if crop == "rice" else SOL_REV
    body = {
        "modelId": model,
        "modelVersion": version,
        "candidates": [
            {"rawLabel": top, "confidence": conf},
            {"rawLabel": second, "confidence": second_conf},
        ],
        "latencyMs": 12,
    }
    (VISION_DIR / f"{sha}.json").write_text(json.dumps(body, indent=2) + "\n", encoding="utf-8")
    sidecar = {
        "mode": "LIVE",
        "crop_code": crop,
        "model_id": model,
        "model_version": version,
        "model_role": "primary",
        "fallback_used": False,
        "fallback_reason": None,
        "architecture": "swin" if crop == "rice" else "mobilenetv2",
        "image_sha256": sha,
        "predictions": [
            {"raw_label": top, "confidence": conf, "rank": 1},
            {"raw_label": second, "confidence": second_conf, "rank": 2},
        ],
        "inference_ms": 12,
        "correlation_id": "demo",
    }
    SIDECAR_CLASSIFY.mkdir(parents=True, exist_ok=True)
    (SIDECAR_CLASSIFY / f"{sha}.json").write_text(json.dumps(sidecar, indent=2) + "\n", encoding="utf-8")
    if EXISTING_GRADCAM.is_file():
        overlay = EXISTING_GRADCAM.read_bytes()
        (GRADCAM_DIR / f"{sha}.png").write_bytes(overlay)
        SIDECAR_EXPLAIN.mkdir(parents=True, exist_ok=True)
        (SIDECAR_EXPLAIN / f"{sha}.png").write_bytes(overlay)


def hash_embed_vector(text: str) -> list[float]:
    dims: list[float] = []
    for i in range(192):
        digest = hashlib.md5(f"{text}:{i}".encode("utf-8")).digest()
        for b in digest[:4]:
            dims.append((b / 127.5) - 1.0)
    norm = math.sqrt(sum(v * v for v in dims)) or 1.0
    return [v / norm for v in dims]


def generate_assets() -> dict[str, str]:
    DEMO_IMAGES.mkdir(parents=True, exist_ok=True)
    DEMO_AUDIO.mkdir(parents=True, exist_ok=True)
    specs = [
        ("01-rice-blast-primary.jpg", "rice", "Leaf Blast", 0.91, "Brown Spot", 0.04, False),
        ("02-rice-brown-spot-ambiguous.jpg", "rice", "Brown Spot", 0.60, "Leaf Blast", 0.28, False),
        ("03-rice-brown-spot-primary.jpg", "rice", "Brown Spot", 0.88, "Healthy", 0.05, False),
        ("04-tomato-early-blight.jpg", "tomato", "Tomato___Early_blight", 0.86, "Tomato___healthy", 0.06, False),
        ("05-potato-late-blight.jpg", "potato", "Potato___Late_blight", 0.84, "Potato___Early_blight", 0.09, False),
        ("06-rice-healthy.jpg", "rice", "Healthy", 0.93, "Brown Spot", 0.03, False),
        ("07-blurry-reject.jpg", "rice", "Healthy", 0.40, "Brown Spot", 0.20, True),
    ]
    hashes: dict[str, str] = {}
    missing = [name for name, *_ in specs if not (DEMO_IMAGES / name).is_file()]
    if missing:
        raise SystemExit(
            "demo photographs missing: "
            + ", ".join(missing)
            + ". Place Wikimedia JPEGs in docs/demo/images/ then re-run."
        )
    for name, crop, top, conf, second, second_conf, blurry in specs:
        path = DEMO_IMAGES / name
        digest = sha256_file(path)
        hashes[name] = digest
        if not blurry:
            write_vision_fixture(digest, crop, top, conf, second, second_conf)

    audio = DEMO_AUDIO / "secondary-brown-spot.wav"
    if not audio.is_file():
        write_wav(audio)
    audio_sha = sha256_file(audio)
    hashes[audio.name] = audio_sha
    asr = {
        "modelId": ASR_MODEL,
        "transcriptBn": SECONDARY_TRANSCRIPT,
        "asrConfidence": 0.91,
        "latencyMs": 40,
    }
    ASR_DIR.mkdir(parents=True, exist_ok=True)
    (ASR_DIR / f"{audio_sha}.json").write_text(json.dumps(asr, indent=2) + "\n", encoding="utf-8")
    sidecar_asr = {
        "mode": "LIVE",
        "model_id": ASR_MODEL,
        "model_version": ASR_REV,
        "audio_sha256": audio_sha,
        "language": "bn",
        "duration_ms": 1200,
        "sample_rate_hz": 16000,
        "speech_detected": True,
        "transcript": SECONDARY_TRANSCRIPT,
        "confidence": 0.91,
        "segments": [],
        "inference_ms": 40,
        "correlation_id": "demo",
    }
    SIDECAR_ASR.mkdir(parents=True, exist_ok=True)
    (SIDECAR_ASR / f"{audio_sha}.json").write_text(json.dumps(sidecar_asr, indent=2) + "\n", encoding="utf-8")

    embed_key = hashlib.sha256(SECONDARY_TRANSCRIPT.encode("utf-8")).hexdigest()
    embed_body = {
        "modelId": EMBED_MODEL,
        "vector": hash_embed_vector(bangla_normalise(SECONDARY_TRANSCRIPT)),
        "latencyMs": 4,
    }
    EMBED_DIR.mkdir(parents=True, exist_ok=True)
    (EMBED_DIR / f"{embed_key}.json").write_text(json.dumps(embed_body) + "\n", encoding="utf-8")
    hashes["embed_transcript_sha"] = embed_key

    index = {}
    if SIDECAR_INDEX.is_file():
        index = json.loads(SIDECAR_INDEX.read_text(encoding="utf-8"))
    for name, digest in hashes.items():
        if name.endswith(".jpg") and not name.startswith("07"):
            index[digest] = {
                "endpoint": "/v1/vision/classify",
                "source": name,
                "recorded_at": "2026-09-07T20:00:00Z",
                "note": "DEMO field photograph (Wikimedia Commons); replay fixture keyed by SHA-256",
            }
        if name.endswith(".wav"):
            index[digest] = {
                "endpoint": "/v1/asr/transcribe",
                "source": name,
                "recorded_at": "2026-09-07T20:00:00Z",
                "note": "DEMO 16 kHz tone; paired with a Bangla symptom transcript fixture",
            }
    SIDECAR_INDEX.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    manifest = DEMO_IMAGES.parent / "manifest.json"
    manifest.write_text(
        json.dumps({"transcript_bn": SECONDARY_TRANSCRIPT, "files": hashes}, indent=2, ensure_ascii=False)
        + "\n",
        encoding="utf-8",
    )
    return hashes


def main() -> None:
    SQL_OUT.parent.mkdir(parents=True, exist_ok=True)
    SQL_OUT.write_text(emit_sql(), encoding="utf-8")
    hashes = generate_assets()
    print(f"wrote {SQL_OUT}")
    for name, digest in hashes.items():
        print(f"{name} {digest}")


if __name__ == "__main__":
    main()

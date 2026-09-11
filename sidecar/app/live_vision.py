"""LIVE vision: load one backbone (ViT or EfficientNet-B3), classify, cache by digest."""

from __future__ import annotations

import time
from collections import OrderedDict
from dataclasses import dataclass
from io import BytesIO
from typing import Any, Protocol

from app.config import (
    BACKEND_VISIONARY,
    BACKEND_VIT,
    ROLE_VISION_RICE_PRIMARY,
    VISIONARY_MODEL_ID,
    ModelSpec,
    Settings,
)
from app.errors import SidecarError, bad_request, inference_failed, model_unavailable, undecodable
from app.replay import sha256_hex
from app.vision import EXPLAIN_METHOD, _clip_predictions, read_image, route_crop

CACHE_MAX = 64
EFFICIENTNET_WEIGHT_FILE = "best_crop_disease_model.pt"
EFFICIENTNET_INPUT_PX = 300
EFFICIENTNET_NUM_CLASSES = 17
IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)

# Native ImageFolder order for VisionaryQuant/5_Crop_Disease_Detection. Index 3 is
# Corn___Northern_Leaf_Blight, not Invalid. Sugarcane uses two underscores.
EFFICIENTNET_LABELS: tuple[str, ...] = (
    "Corn___Common_Rust",
    "Corn___Gray_Leaf_Spot",
    "Corn___Healthy",
    "Corn___Northern_Leaf_Blight",
    "Potato___Early_Blight",
    "Potato___Healthy",
    "Potato___Late_Blight",
    "Rice___Brown_Spot",
    "Rice___Healthy",
    "Rice___Leaf_Blast",
    "Rice___Neck_Blast",
    "Sugarcane__Bacterial_Blight",
    "Sugarcane__Healthy",
    "Sugarcane__Red_Rot",
    "Wheat___Brown_Rust",
    "Wheat___Healthy",
    "Wheat___Yellow_Rust",
)


class VisionRuntime(Protocol):
    model_id: str
    model_version: str
    architecture: str
    labels: list[str]

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        ...

    def gradcam(self, image_bytes: bytes, target_label: str | None) -> tuple[Any, str]:
        ...


@dataclass
class TransformersRuntime:
    model_id: str
    model_version: str
    architecture: str
    labels: list[str]
    processor: Any
    model: Any

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        from PIL import Image, UnidentifiedImageError
        import torch
        from torch.nn.functional import softmax

        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except (UnidentifiedImageError, OSError) as exc:
            raise undecodable("image bytes could not be decoded") from exc
        inputs = self.processor(images=image, return_tensors="pt")
        with torch.no_grad():
            logits = self.model(**inputs).logits
            probs = softmax(logits, dim=-1)[0]
        ranked: list[tuple[str, float]] = []
        for index, confidence in enumerate(probs.tolist()):
            label = self.labels[index] if index < len(self.labels) else str(index)
            ranked.append((label, float(confidence)))
        ranked.sort(key=lambda item: item[1], reverse=True)
        return ranked

    def gradcam(self, image_bytes: bytes, target_label: str | None) -> tuple[Any, str]:
        from PIL import Image, ImageOps, UnidentifiedImageError

        try:
            image = ImageOps.exif_transpose(Image.open(BytesIO(image_bytes))).convert("RGB")
        except (UnidentifiedImageError, OSError, ValueError) as exc:
            raise undecodable("image bytes could not be decoded") from exc
        class_idx = _label_index(self.labels, target_label)
        inputs = self.processor(images=image, return_tensors="pt")
        pixel = inputs.get("pixel_values")
        if pixel is None:
            raise inference_failed("vision processor produced no pixel_values")
        inputs["pixel_values"] = pixel.detach().requires_grad_(True)

        def _forward(_tensor: Any) -> Any:
            return self.model(**inputs).logits

        target = _transformer_cam_target(self.model)
        cam, class_idx = _gradcam(self.model, target, inputs["pixel_values"], class_idx, _forward)
        return cam, self.labels[class_idx]


@dataclass
class EfficientNetRuntime:
    model_id: str
    model_version: str
    architecture: str
    labels: list[str]
    transform: Any
    model: Any

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        from PIL import Image, UnidentifiedImageError
        import torch
        from torch.nn.functional import softmax

        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except (UnidentifiedImageError, OSError) as exc:
            raise undecodable("image bytes could not be decoded") from exc
        tensor = self.transform(image).unsqueeze(0)
        with torch.no_grad():
            logits = self.model(tensor)
            probs = softmax(logits, dim=-1)[0]
            scores = probs.tolist()
        del logits, probs, tensor
        ranked: list[tuple[str, float]] = []
        for index, confidence in enumerate(scores):
            label = self.labels[index] if index < len(self.labels) else str(index)
            ranked.append((label, float(confidence)))
        ranked.sort(key=lambda item: item[1], reverse=True)
        return ranked

    def gradcam(self, image_bytes: bytes, target_label: str | None) -> tuple[Any, str]:
        from PIL import Image, ImageOps, UnidentifiedImageError

        try:
            image = ImageOps.exif_transpose(Image.open(BytesIO(image_bytes))).convert("RGB")
        except (UnidentifiedImageError, OSError, ValueError) as exc:
            raise undecodable("image bytes could not be decoded") from exc
        class_idx = _label_index(self.labels, target_label)
        tensor = self.transform(image).unsqueeze(0)
        tensor = tensor.detach().requires_grad_(True)
        target = _efficientnet_cam_target(self.model)
        cam, class_idx = _gradcam(self.model, target, tensor, class_idx, self.model)
        return cam, self.labels[class_idx]


class ClassifyCache:
    def __init__(self, max_size: int = CACHE_MAX) -> None:
        self._max = max_size
        self._hits: OrderedDict[tuple[str, str, int], list[dict[str, Any]]] = OrderedDict()

    def get(self, digest: str, crop_code: str, top_k: int) -> list[dict[str, Any]] | None:
        key = (digest, crop_code, top_k)
        if key not in self._hits:
            return None
        self._hits.move_to_end(key)
        return [dict(row) for row in self._hits[key]]

    def put(self, digest: str, crop_code: str, top_k: int, predictions: list[dict[str, Any]]) -> None:
        key = (digest, crop_code, top_k)
        self._hits[key] = [dict(row) for row in predictions]
        self._hits.move_to_end(key)
        while len(self._hits) > self._max:
            self._hits.popitem(last=False)


class ExplainCache:
    def __init__(self, max_size: int = CACHE_MAX) -> None:
        self._max = max_size
        self._hits: OrderedDict[tuple[str, str], tuple[bytes, str]] = OrderedDict()

    def get(self, digest: str, target_label: str) -> tuple[bytes, str] | None:
        key = (digest, target_label)
        if key not in self._hits:
            return None
        self._hits.move_to_end(key)
        png, label = self._hits[key]
        return png, label

    def put(self, digest: str, target_label: str, png: bytes, resolved_label: str) -> None:
        key = (digest, target_label)
        self._hits[key] = (png, resolved_label)
        self._hits.move_to_end(key)
        while len(self._hits) > self._max:
            self._hits.popitem(last=False)


# Matplotlib viridis samples (sRGB 0-255). Expanded to 256 entries with integer lerp.
_VIRIDIS_STOPS: tuple[tuple[int, int, int], ...] = (
    (68, 1, 84),
    (71, 44, 122),
    (59, 81, 139),
    (44, 113, 142),
    (33, 144, 141),
    (39, 173, 129),
    (92, 200, 99),
    (170, 220, 50),
    (253, 231, 37),
)


def _lut256(stops: tuple[tuple[int, int, int], ...]) -> tuple[tuple[int, int, int], ...]:
    n = len(stops) - 1
    rows: list[tuple[int, int, int]] = []
    for i in range(256):
        t_num = i * n
        j = min(t_num // 255, n - 1)
        rem = t_num - j * 255
        a = stops[j]
        b = stops[j + 1]
        inv = 255 - rem
        rows.append(
            (
                (a[0] * inv + b[0] * rem + 127) // 255,
                (a[1] * inv + b[1] * rem + 127) // 255,
                (a[2] * inv + b[2] * rem + 127) // 255,
            )
        )
    return tuple(rows)


VIRIDIS_LUT: tuple[tuple[int, int, int], ...] = _lut256(_VIRIDIS_STOPS)
PNG_COMPRESS_LEVEL = 6


def _label_index(labels: list[str], target_label: str | None) -> int | None:
    if not target_label:
        return None
    try:
        return labels.index(target_label)
    except ValueError:
        raise bad_request("target_label is not in the routed model's label space") from None


def _efficientnet_cam_target(model: Any) -> Any:
    # timm EfficientNet.forward_features is conv_head → bn2. Analogue of SIDECAR-FR-033.
    # At 300×300 input the map is 10×10. Newer timm fuses SiLU into bn2 (BatchNormAct);
    # older timm exposes a separate act2 after bn2.
    target = getattr(model, "act2", None)
    if target is not None:
        return target
    target = getattr(model, "bn2", None)
    if target is not None:
        return target
    raise inference_failed("EfficientNet has no act2/bn2 for Grad-CAM")


def _transformer_cam_target(model: Any) -> Any:
    vit = getattr(model, "vit", None)
    if vit is not None and hasattr(vit, "layernorm"):
        return vit.layernorm
    swin = getattr(model, "swin", None)
    if swin is not None and hasattr(swin, "layernorm"):
        return swin.layernorm
    base = getattr(model, "base_model", None)
    if base is not None and hasattr(base, "layernorm"):
        return base.layernorm
    if hasattr(model, "layernorm"):
        return model.layernorm
    raise inference_failed("no encoder layer-norm found for Grad-CAM")


def _sequence_cam(tokens: Any, grads: Any) -> Any:
    seq = int(tokens.shape[0])
    side = int(seq**0.5)
    start = 0
    if side * side != seq:
        rest = seq - 1
        side = int(rest**0.5)
        if side * side != rest:
            raise inference_failed("cannot reshape encoder tokens to a square grid")
        start = 1
    patches = tokens[start:].reshape(side, side, -1)
    g = grads[start:].reshape(side, side, -1)
    weights = g.mean(dim=(0, 1))
    return (patches * weights).sum(dim=-1).clamp(min=0)


def _spatial_cam(act: Any, grad: Any) -> Any:
    a = act[0].detach()
    g = grad[0]
    if a.ndim == 3:
        if a.shape[1] == a.shape[2]:
            weights = g.mean(dim=(1, 2))
            return (weights[:, None, None] * a).sum(dim=0).clamp(min=0)
        weights = g.mean(dim=(0, 1))
        return (a * weights).sum(dim=-1).clamp(min=0)
    if a.ndim == 2:
        return _sequence_cam(a, g)
    raise inference_failed("unexpected activation rank for Grad-CAM")


def _gradcam(
    model: Any,
    target_module: Any,
    input_tensor: Any,
    class_idx: int | None,
    forward_fn: Any,
) -> tuple[Any, int]:
    import torch

    stored: dict[str, Any] = {}

    def _fwd_hook(_module: Any, _inputs: Any, output: Any) -> None:
        stored["act"] = output

        def _save_grad(grad: Any) -> None:
            stored["grad"] = grad

        output.register_hook(_save_grad)

    handle = target_module.register_forward_hook(_fwd_hook)
    try:
        model.zero_grad(set_to_none=True)
        with torch.enable_grad():
            logits = forward_fn(input_tensor)
            if class_idx is None:
                class_idx = int(logits[0].argmax().item())
            logits[0, class_idx].backward()
        if "act" not in stored or "grad" not in stored:
            raise inference_failed("Grad-CAM hooks did not capture activations")
        cam = _spatial_cam(stored["act"], stored["grad"])
        return cam, int(class_idx)
    finally:
        handle.remove()
        model.zero_grad(set_to_none=True)


def _hwc_u8_bytes(hwc: Any) -> bytes:
    import torch

    cpu = hwc.contiguous().cpu().to(torch.uint8).reshape(-1)
    try:
        return cpu.numpy().tobytes()
    except (RuntimeError, ModuleNotFoundError, ImportError):
        return bytes(cpu.tolist())


def _oriented_rgb(image_bytes: bytes):
    from PIL import Image, ImageOps, UnidentifiedImageError

    try:
        image = ImageOps.exif_transpose(Image.open(BytesIO(image_bytes))).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        raise undecodable("image bytes could not be decoded") from exc
    return image


def _fit_longest_edge(image: Any, max_edge: int) -> Any:
    from PIL import Image

    width, height = image.size
    longest = max(width, height)
    if longest <= max_edge:
        return image
    new_w = max(1, (width * max_edge) // longest)
    new_h = max(1, (height * max_edge) // longest)
    return image.resize((new_w, new_h), Image.Resampling.BILINEAR)


def compose_overlay(image_bytes: bytes, cam: Any, max_edge: int, alpha: float) -> bytes:
    """Pre-composite a viridis heat map over the EXIF-oriented photo. Returns RGB PNG bytes."""
    from PIL import Image
    import torch
    from torch.nn.functional import interpolate

    photo = _oriented_rgb(image_bytes)
    photo = _fit_longest_edge(photo, max_edge)
    out_w, out_h = photo.size
    cam_t = cam.detach().float() if hasattr(cam, "detach") else torch.tensor(cam, dtype=torch.float32)
    if cam_t.ndim != 2:
        raise inference_failed("Grad-CAM map must be rank-2")
    up = interpolate(
        cam_t.unsqueeze(0).unsqueeze(0),
        size=(out_h, out_w),
        mode="bilinear",
        align_corners=False,
    )[0, 0]
    lo = up.min()
    hi = up.max()
    denom = (hi - lo).clamp(min=1e-8)
    norm = (up - lo) / denom
    idx = (norm * 255).clamp(0, 255).to(torch.long)
    lut = torch.tensor(VIRIDIS_LUT, dtype=torch.uint8)
    heat = lut[idx]
    heat_img = Image.frombytes("RGB", (out_w, out_h), _hwc_u8_bytes(heat))
    overlay = heat_img.convert("RGBA")
    overlay.putalpha(int(round(alpha * 255)))
    blended = Image.alpha_composite(photo.convert("RGBA"), overlay).convert("RGB")
    buffer = BytesIO()
    blended.save(buffer, format="PNG", optimize=False, compress_level=PNG_COMPRESS_LEVEL)
    return buffer.getvalue()


def assert_backend_matches_model(backend: str, model_id: str) -> None:
    is_visionary = model_id == VISIONARY_MODEL_ID
    if backend == BACKEND_VISIONARY and not is_visionary:
        raise SystemExit(
            f"FOSHOL_SIDECAR_VISION_BACKEND=visionary requires {VISIONARY_MODEL_ID}, got {model_id}"
        )
    if backend == BACKEND_VIT and is_visionary:
        raise SystemExit(
            f"FOSHOL_SIDECAR_VISION_BACKEND=vit cannot load {VISIONARY_MODEL_ID}; "
            "use visionary or a Transformers vision model"
        )


def load_runtime(spec: ModelSpec, torch_threads: int, backend: str = BACKEND_VIT) -> VisionRuntime:
    assert_backend_matches_model(backend, spec.model_id)
    if backend == BACKEND_VISIONARY:
        return _load_efficientnet_runtime(spec, torch_threads)
    return _load_transformers_runtime(spec, torch_threads)


def _load_transformers_runtime(spec: ModelSpec, torch_threads: int) -> TransformersRuntime:
    import torch
    from transformers import AutoImageProcessor, AutoModelForImageClassification

    torch.set_num_threads(max(torch_threads, 1))
    processor = AutoImageProcessor.from_pretrained(spec.model_id, revision=spec.model_version)
    model = AutoModelForImageClassification.from_pretrained(spec.model_id, revision=spec.model_version)
    model.eval()
    id2label = {int(k): str(v) for k, v in model.config.id2label.items()}
    labels = [id2label[i] for i in range(len(id2label))]
    architecture = str(getattr(model.config, "model_type", None) or "vit")
    runtime = TransformersRuntime(
        model_id=spec.model_id,
        model_version=spec.model_version,
        architecture=architecture,
        labels=labels,
        processor=processor,
        model=model,
    )
    _warmup(runtime, edge_px=224)
    return runtime


def _load_efficientnet_runtime(spec: ModelSpec, torch_threads: int) -> EfficientNetRuntime:
    import torch
    import torch.nn as nn
    import timm
    from huggingface_hub import hf_hub_download
    from torchvision import transforms

    torch.set_num_threads(max(torch_threads, 1))
    weight_path = hf_hub_download(
        repo_id=spec.model_id,
        filename=EFFICIENTNET_WEIGHT_FILE,
        revision=spec.model_version,
    )
    # Checkpoint is a timm EfficientNet-B3 state_dict (conv_stem / blocks / classifier.0),
    # not torchvision (features / classifier.1).
    model = timm.create_model("efficientnet_b3", pretrained=False)
    in_features = model.classifier.in_features
    model.classifier = nn.Sequential(nn.Linear(in_features, EFFICIENTNET_NUM_CLASSES))
    state = torch.load(weight_path, map_location="cpu", weights_only=True)
    model.load_state_dict(state)
    model.eval()
    transform = transforms.Compose(
        [
            transforms.Resize((EFFICIENTNET_INPUT_PX, EFFICIENTNET_INPUT_PX)),
            transforms.ToTensor(),
            transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ]
    )
    runtime = EfficientNetRuntime(
        model_id=spec.model_id,
        model_version=spec.model_version,
        architecture="efficientnet_b3",
        labels=list(EFFICIENTNET_LABELS),
        transform=transform,
        model=model,
    )
    _warmup(runtime, edge_px=EFFICIENTNET_INPUT_PX)
    return runtime


def _warmup(runtime: VisionRuntime, edge_px: int = 224) -> None:
    from PIL import Image

    buffer = BytesIO()
    Image.new("RGB", (edge_px, edge_px), color=(16, 120, 16)).save(buffer, format="PNG")
    runtime.predict(buffer.getvalue())


def install_live_vision(state: Any) -> None:
    settings: Settings = state.settings
    spec = settings.models.get(ROLE_VISION_RICE_PRIMARY)
    if spec is None:
        raise SystemExit("LIVE mode requires vision.rice.primary to be configured")
    runtime = load_runtime(spec, settings.torch_threads, settings.vision_backend)
    state.vision_runtime = runtime
    state.classify_cache = ClassifyCache()
    state.explain_cache = ExplainCache()
    state.loaded_flags[ROLE_VISION_RICE_PRIMARY] = True
    state.loaded_at[ROLE_VISION_RICE_PRIMARY] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    state.labels[ROLE_VISION_RICE_PRIMARY] = list(runtime.labels)
    state.models_loaded = 1
    state.degraded_reasons = [
        "vision.rice.fallback not loaded",
        "vision.solanaceae not loaded",
        "asr.bangla not loaded",
        "embed.text not loaded",
    ]


def classify_live(
    settings: Settings,
    runtime: VisionRuntime,
    cache: ClassifyCache,
    image: bytes,
    crop_code: str,
    top_k: int,
    model_role: str | None,
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    read_image(settings, image)
    route = route_crop(
        settings,
        crop_code,
        model_role,
        primary_usable=True,
        fallback_usable=False,
        solanaceae_usable=False,
    )
    if route.role != ROLE_VISION_RICE_PRIMARY:
        raise model_unavailable("LIVE vision loads only vision.rice.primary")
    digest = sha256_hex(image)
    cached = cache.get(digest, crop_code, top_k)
    if cached is None:
        try:
            ranked = runtime.predict(image)
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("vision forward pass failed") from exc
        raw = [{"raw_label": label, "confidence": confidence, "rank": 0} for label, confidence in ranked]
        predictions = _clip_predictions(raw, top_k)
        cache.put(digest, crop_code, top_k, predictions)
    else:
        predictions = cached
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "LIVE",
        "crop_code": crop_code,
        "model_id": runtime.model_id,
        "model_version": runtime.model_version,
        "model_role": route.model_role,
        "fallback_used": route.fallback_used,
        "fallback_reason": route.fallback_reason,
        "architecture": runtime.architecture,
        "image_sha256": digest,
        "predictions": predictions,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def explain_live(
    settings: Settings,
    runtime: VisionRuntime,
    cache: ExplainCache,
    image: bytes,
    crop_code: str,
    model_role: str | None,
    target_label: str | None,
    alpha: float | None,
) -> tuple[bytes, dict[str, str], int]:
    started = time.perf_counter()
    read_image(settings, image)
    route = route_crop(
        settings,
        crop_code,
        model_role,
        primary_usable=True,
        fallback_usable=False,
        solanaceae_usable=False,
    )
    if route.role != ROLE_VISION_RICE_PRIMARY:
        raise model_unavailable("LIVE vision loads only vision.rice.primary")
    requested = (target_label or "").strip()
    digest = sha256_hex(image)
    overlay_alpha = settings.explain_alpha if alpha is None else alpha
    cached = cache.get(digest, requested)
    if cached is None:
        try:
            cam, resolved = runtime.gradcam(image, requested or None)
            png = compose_overlay(image, cam, settings.explain_max_edge_px, overlay_alpha)
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("vision explain failed") from exc
        cache.put(digest, requested, png, resolved)
    else:
        png, resolved = cached
    method = EXPLAIN_METHOD.get(runtime.architecture, "gradcam")
    headers = {
        "X-Foshol-Model-Id": runtime.model_id,
        "X-Foshol-Model-Version": runtime.model_version,
        "X-Foshol-Target-Label": resolved,
        "X-Foshol-Method": method,
        "X-Foshol-Mode": "LIVE",
        "Content-Type": "image/png",
    }
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return png, headers, inference_ms


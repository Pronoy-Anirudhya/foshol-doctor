package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.BanglaNormalizer;
import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.intake.api.IntakeAudio;
import com.rootcause.foshol.intake.api.IntakeImage;
import com.rootcause.foshol.intake.api.IntakeRequest;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.application.port.DuplicateIdempotencyKeyException;
import com.rootcause.foshol.intake.application.port.ImageQualityPort;
import com.rootcause.foshol.intake.application.port.ImageTransformPort;
import com.rootcause.foshol.intake.application.port.ObjectStorePort;
import com.rootcause.foshol.intake.domain.AudioHeaderReader;
import com.rootcause.foshol.intake.domain.CaseAudio;
import com.rootcause.foshol.intake.domain.CaseImage;
import com.rootcause.foshol.intake.domain.ContentTypeSniffer;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import com.rootcause.foshol.intake.domain.QualityReason;
import com.rootcause.foshol.intake.domain.spec.IdempotencyReplaySpec;
import com.rootcause.foshol.intake.domain.spec.ImageQualitySpec;
import com.rootcause.foshol.intake.domain.spec.ResubmissionParentSpec;
import com.rootcause.foshol.intake.domain.vo.AudioId;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.ImageId;
import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import com.rootcause.foshol.intake.domain.vo.QualityVerdict;
import com.rootcause.foshol.intake.domain.vo.RequestFingerprint;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SubmitCaseCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SubmitCaseCommandHandler.class);
    static final String ENDPOINT = "POST /api/v1/cases";

    private final DiagnosisCaseRepository cases;
    private final ObjectStorePort store;
    private final ImageQualityPort qualityPort;
    private final ImageTransformPort transformPort;
    private final FarmerLookupApi farmers;
    private final KnowledgeQueryApi knowledge;
    private final CaseSubmissionWriter writer;
    private final Clock clock;
    private final int minImages;
    private final int maxImages;
    private final int maxImageBytes;
    private final int maxAudioBytes;
    private final int maxAudioSeconds;
    private final Duration idempotencyTtl;
    private final int rateMax;
    private final Duration rateWindow;
    private final int derivativeMaxEdgePx;
    private final Set<String> allowedImageTypes;
    private final Set<String> allowedAudioTypes;
    private final ImageQualitySpec qualitySpec;

    public SubmitCaseCommandHandler(
            DiagnosisCaseRepository cases,
            ObjectStorePort store,
            ImageQualityPort qualityPort,
            ImageTransformPort transformPort,
            FarmerLookupApi farmers,
            KnowledgeQueryApi knowledge,
            CaseSubmissionWriter writer,
            Clock clock,
            @Value("${" + ConfigKeys.INTAKE_MIN_IMAGES + "}") int minImages,
            @Value("${" + ConfigKeys.INTAKE_MAX_IMAGES + "}") int maxImages,
            @Value("${" + ConfigKeys.INTAKE_MAX_IMAGE_BYTES + "}") int maxImageBytes,
            @Value("${" + ConfigKeys.INTAKE_MAX_AUDIO_BYTES + "}") int maxAudioBytes,
            @Value("${" + ConfigKeys.INTAKE_MAX_AUDIO_SECONDS + "}") int maxAudioSeconds,
            @Value("${" + ConfigKeys.INTAKE_IDEMPOTENCY_TTL + "}") Duration idempotencyTtl,
            @Value("${" + ConfigKeys.INTAKE_RATE_LIMIT_MAX_CASES + "}") int rateMax,
            @Value("${" + ConfigKeys.INTAKE_RATE_LIMIT_WINDOW + "}") Duration rateWindow,
            @Value("${" + ConfigKeys.INTAKE_QUALITY_BLUR_VARIANCE_MIN + "}") double blurMin,
            @Value("${" + ConfigKeys.INTAKE_QUALITY_EXPOSURE_MIN + "}") double exposureMin,
            @Value("${" + ConfigKeys.INTAKE_QUALITY_EXPOSURE_MAX + "}") double exposureMax,
            @Value("${" + ConfigKeys.INTAKE_QUALITY_MIN_EDGE_PX + "}") int minEdgePx,
            @Value("${" + ConfigKeys.STORAGE_DERIVATIVE_MAX_EDGE_PX + "}") int derivativeMaxEdgePx,
            @Value("${" + ConfigKeys.INTAKE_ALLOWED_IMAGE_TYPES + "}") String allowedImageTypes,
            @Value("${" + ConfigKeys.INTAKE_ALLOWED_AUDIO_TYPES + "}") String allowedAudioTypes) {
        this.cases = cases;
        this.store = store;
        this.qualityPort = qualityPort;
        this.transformPort = transformPort;
        this.farmers = farmers;
        this.knowledge = knowledge;
        this.writer = writer;
        this.clock = clock;
        this.minImages = minImages;
        this.maxImages = maxImages;
        this.maxImageBytes = maxImageBytes;
        this.maxAudioBytes = maxAudioBytes;
        this.maxAudioSeconds = maxAudioSeconds;
        this.idempotencyTtl = idempotencyTtl;
        this.rateMax = rateMax;
        this.rateWindow = rateWindow;
        this.derivativeMaxEdgePx = derivativeMaxEdgePx;
        this.allowedImageTypes = csv(allowedImageTypes);
        this.allowedAudioTypes = csv(allowedAudioTypes);
        this.qualitySpec = new ImageQualitySpec(blurMin, exposureMin, exposureMax, minEdgePx);
    }

    public SubmitCaseResult handle(IntakeRequest request) {
        return handle(SubmitCaseCommand.from(request));
    }

    public SubmitCaseResult handle(SubmitCaseCommand command) {
        Instant now = clock.instant();
        cases.deleteExpiredIdempotency(now);
        if (command.idempotencyKey() == null) {
            throw new IntakeException(ErrorCodes.ERR_IDEMPOTENCY_KEY_MISSING, 400, "Idempotency-Key is required.");
        }
        List<IntakeImage> images = command.images() == null ? List.of() : command.images();
        if (images.size() < minImages || images.size() > maxImages) {
            throw new IntakeException(ErrorCodes.ERR_IMAGE_COUNT, 400, "Image count is outside the allowed range.");
        }
        FarmerView farmer = farmers.findById(command.farmerId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 400, "Farmer was not found."));
        CropView crop = knowledge
                .findCropById(command.cropId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CROP_NOT_FOUND, 400, "Crop was not found."));
        CaseId parentId = validateParent(command.parentCaseId(), farmer.id());
        Instant windowStart = now.minus(rateWindow);
        if (cases.countByFarmerSince(farmer.id(), windowStart) >= rateMax) {
            Instant oldest = cases.oldestCreatedAtAfter(farmer.id(), windowStart).orElse(windowStart);
            long retry = Math.max(1, Duration.between(now, oldest.plus(rateWindow)).toSeconds());
            throw new IntakeException(ErrorCodes.ERR_CASE_RATE_LIMITED, 429, "Too many cases submitted.", retry);
        }
        List<PreparedImage> prepared = prepareImages(images);
        PreparedAudio audio = prepareAudio(command.audio());
        String note = normaliseNote(command.noteBn());
        RequestFingerprint fingerprint = RequestFingerprint.compute(
                farmer.id(),
                crop.id(),
                note == null ? "" : note,
                parentId == null ? null : parentId.value(),
                prepared.stream().map(PreparedImage::sha).toList(),
                audio == null ? null : audio.sha());
        Optional<IdempotencyRecord> existing = cases.findIdempotency(command.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), farmer.id(), fingerprint);
        }
        CaseId caseId = CaseId.newId();
        List<String> storedKeys = new ArrayList<>();
        try {
            List<CaseImage> domainImages = storeImages(caseId, prepared, storedKeys);
            CaseAudio domainAudio = storeAudio(caseId, audio, storedKeys);
            DiagnosisCase diagnosisCase = DiagnosisCase.submit(
                    caseId,
                    farmer.id(),
                    crop.id(),
                    parentId,
                    note,
                    farmer.districtCode(),
                    CorrelationId.currentOrCreate(),
                    now,
                    domainImages,
                    domainAudio,
                    minImages,
                    maxImages);
            String body = "{\"caseId\":\""
                    + caseId.value()
                    + "\",\"status\":\"SUBMITTED\",\"submittedAt\":\""
                    + diagnosisCase.createdAt()
                    + "\"}";
            persist(diagnosisCase, command, fingerprint, body, now, crop);
            return SubmitCaseResult.accepted(caseId.value(), body);
        } catch (DuplicateIdempotencyKeyException ex) {
            storedKeys.forEach(store::deleteQuietly);
            IdempotencyRecord stored = cases.findIdempotency(command.idempotencyKey())
                    .orElseThrow(() -> new IntakeException(
                            ErrorCodes.ERR_IDEMPOTENCY_KEY_CONFLICT, 409, "Idempotency key was reused with a different request."));
            return replayOrConflict(stored, farmer.id(), fingerprint);
        } catch (RuntimeException ex) {
            storedKeys.forEach(store::deleteQuietly);
            if (ex instanceof IntakeException intake) {
                throw intake;
            }
            log.warn("storage unavailable correlationId={}", CorrelationId.current(), ex);
            throw new IntakeException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Object storage is unavailable.");
        }
    }

    private void persist(
            DiagnosisCase diagnosisCase,
            SubmitCaseCommand command,
            RequestFingerprint fingerprint,
            String body,
            Instant now,
            CropView crop) {
        writer.write(
                diagnosisCase,
                new IdempotencyRecord(
                        command.idempotencyKey(),
                        command.farmerId(),
                        ENDPOINT,
                        fingerprint.hash(),
                        202,
                        body,
                        now,
                        now.plus(idempotencyTtl)),
                toSubmitted(diagnosisCase, crop.code()));
        String thumb = diagnosisCase.primaryImage().derivativeObjectKey() == null
                ? diagnosisCase.primaryImage().objectKey().value()
                : diagnosisCase.primaryImage().derivativeObjectKey().value();
        cases.upsertFarmerHistory(diagnosisCase, crop.nameBn(), thumb);
    }

    private SubmitCaseResult replayOrConflict(
            IdempotencyRecord stored, UUID farmerId, RequestFingerprint fingerprint) {
        if (IdempotencyReplaySpec.INSTANCE.isReplay(stored, farmerId, fingerprint)) {
            return SubmitCaseResult.replay(stored.responseBody());
        }
        throw new IntakeException(
                ErrorCodes.ERR_IDEMPOTENCY_KEY_CONFLICT, 409, "Idempotency key was reused with a different request.");
    }

    private CaseId validateParent(UUID parentCaseId, UUID farmerId) {
        if (parentCaseId == null) {
            return null;
        }
        Optional<DiagnosisCase> parent = cases.findById(CaseId.of(parentCaseId));
        if (parent.isEmpty() || !ResubmissionParentSpec.INSTANCE.isSatisfied(parent.get(), farmerId)) {
            throw new IntakeException(ErrorCodes.ERR_PARENT_CASE_INVALID, 400, "The parent case is not valid.");
        }
        return CaseId.of(parentCaseId);
    }

    private List<PreparedImage> prepareImages(List<IntakeImage> images) {
        List<PreparedImage> prepared = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            IntakeImage image = images.get(i);
            byte[] bytes = image.bytes() == null ? new byte[0] : image.bytes();
            if (bytes.length > maxImageBytes) {
                throw new IntakeException(ErrorCodes.ERR_IMAGE_TOO_LARGE, 413, "An image exceeds the size limit.");
            }
            Optional<String> sniffed = ContentTypeSniffer.sniff(bytes);
            if (sniffed.isEmpty()
                    || !ContentTypeSniffer.isImage(sniffed.get())
                    || !allowedImageTypes.contains(sniffed.get())) {
                throw new IntakeException(
                        ErrorCodes.ERR_UNSUPPORTED_MEDIA_TYPE, 415, "An image is not a supported type.");
            }
            ImageQualityPort.ImageProbe probe;
            try {
                probe = qualityPort.probe(bytes);
            } catch (RuntimeException ex) {
                probe = new ImageQualityPort.ImageProbe(0, 0, 0.0, 0.0);
            }
            ImageMetrics metrics =
                    new ImageMetrics(probe.width(), probe.height(), probe.blurVariance(), probe.exposureScore());
            QualityVerdict verdict = qualitySpec.verdict(metrics);
            if (!verdict.accepted()) {
                errors.add(error(i, verdict.reason()));
                continue;
            }
            prepared.add(new PreparedImage(bytes, sniffed.get(), Sha256.ofBytes(bytes), metrics));
        }
        if (!errors.isEmpty()) {
            throw new IntakeException(
                    ErrorCodes.ERR_IMAGE_QUALITY_REJECTED, 422, "One or more images failed the quality gate.", errors);
        }
        return prepared;
    }

    private PreparedAudio prepareAudio(IntakeAudio audio) {
        if (audio == null) {
            return null;
        }
        byte[] bytes = audio.bytes() == null ? new byte[0] : audio.bytes();
        if (bytes.length > maxAudioBytes) {
            throw new IntakeException(ErrorCodes.ERR_AUDIO_TOO_LARGE, 413, "Audio exceeds the size limit.");
        }
        Optional<String> sniffed = ContentTypeSniffer.sniff(bytes);
        if (sniffed.isEmpty()
                || !ContentTypeSniffer.isAudio(sniffed.get())
                || !allowedAudioTypes.contains(sniffed.get())) {
            throw new IntakeException(ErrorCodes.ERR_UNSUPPORTED_MEDIA_TYPE, 415, "Audio is not a supported type.");
        }
        OptionalInt sampleRate = AudioHeaderReader.sampleRateHz(bytes, sniffed.get());
        if (sampleRate.isEmpty()) {
            throw new IntakeException(ErrorCodes.ERR_AUDIO_UNREADABLE, 400, "Audio could not be read.");
        }
        int durationMs = AudioHeaderReader.durationMs(bytes, sniffed.get()).orElse(Math.max(audio.durationMs(), 0));
        if (durationMs > maxAudioSeconds * 1000) {
            throw new IntakeException(ErrorCodes.ERR_AUDIO_TOO_LONG, 400, "Audio exceeds the duration limit.");
        }
        return new PreparedAudio(bytes, sniffed.get(), Sha256.ofBytes(bytes), durationMs, sampleRate.getAsInt());
    }

    private List<CaseImage> storeImages(CaseId caseId, List<PreparedImage> prepared, List<String> storedKeys) {
        List<CaseImage> domainImages = new ArrayList<>(prepared.size());
        for (int i = 0; i < prepared.size(); i++) {
            PreparedImage item = prepared.get(i);
            ImageId imageId = ImageId.newId();
            String ext = ContentTypeSniffer.extensionFor(item.contentType());
            ObjectKey original = ObjectKey.imageOriginal(caseId, imageId, ext);
            store.put(original.value(), item.bytes(), item.contentType());
            storedKeys.add(original.value());
            ObjectKey derivative = original;
            if (item.metrics().longestEdge() > derivativeMaxEdgePx) {
                Optional<byte[]> jpeg = transformPort.jpegDerivative(
                        item.bytes(),
                        item.metrics().width(),
                        item.metrics().height(),
                        derivativeMaxEdgePx);
                if (jpeg.isPresent()) {
                    derivative = ObjectKey.imageDerivative(caseId, imageId);
                    store.put(derivative.value(), jpeg.get(), ContentTypeSniffer.IMAGE_JPEG);
                    storedKeys.add(derivative.value());
                }
            }
            domainImages.add(new CaseImage(
                    imageId,
                    original,
                    derivative,
                    item.contentType(),
                    item.bytes().length,
                    qualitySpec.quality(item.metrics()),
                    item.sha(),
                    false,
                    i + 1));
        }
        return domainImages;
    }

    private CaseAudio storeAudio(CaseId caseId, PreparedAudio audio, List<String> storedKeys) {
        if (audio == null) {
            return null;
        }
        AudioId audioId = AudioId.newId();
        ObjectKey key = ObjectKey.audio(caseId, audioId, ContentTypeSniffer.extensionFor(audio.contentType()));
        store.put(key.value(), audio.bytes(), audio.contentType());
        storedKeys.add(key.value());
        return new CaseAudio(audioId, key, audio.durationMs(), audio.sampleRateHz(), audio.bytes().length, null, null);
    }

    private static String normaliseNote(String noteBn) {
        if (noteBn == null || noteBn.isBlank()) {
            return null;
        }
        return BanglaNormalizer.forStorage(noteBn);
    }

    private static Set<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, Object> error(int position, QualityReason reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("position", position);
        row.put("reason", httpReason(reason));
        row.put("field", "images[" + position + "]");
        row.put("message", reason.name());
        return row;
    }

    private static String httpReason(QualityReason reason) {
        return switch (reason) {
            case TOO_DARK -> "UNDEREXPOSED";
            case TOO_BRIGHT -> "OVEREXPOSED";
            default -> reason.name();
        };
    }

    private static CaseSubmitted toSubmitted(DiagnosisCase diagnosisCase, String cropCode) {
        List<CaseImageRef> images = diagnosisCase.images().stream()
                .map(image -> new CaseImageRef(
                        image.id().value(),
                        image.objectKey().value(),
                        image.derivativeObjectKey() == null ? null : image.derivativeObjectKey().value(),
                        image.sha256().hex(),
                        image.quality().qualityScore(),
                        image.primary(),
                        image.position()))
                .toList();
        CaseAudioRef audio = diagnosisCase.audio() == null
                ? null
                : new CaseAudioRef(
                        diagnosisCase.audio().id().value(),
                        diagnosisCase.audio().objectKey().value(),
                        diagnosisCase.audio().durationMs(),
                        diagnosisCase.audio().transcriptBn());
        return new CaseSubmitted(
                diagnosisCase.id().value(),
                diagnosisCase.farmerId(),
                diagnosisCase.cropId(),
                cropCode,
                diagnosisCase.districtCode(),
                images,
                audio,
                diagnosisCase.noteBn(),
                diagnosisCase.parentCaseId() == null ? null : diagnosisCase.parentCaseId().value(),
                diagnosisCase.correlationId(),
                diagnosisCase.createdAt());
    }

    private record PreparedImage(byte[] bytes, String contentType, Sha256 sha, ImageMetrics metrics) {}

    private record PreparedAudio(byte[] bytes, String contentType, Sha256 sha, int durationMs, int sampleRateHz) {}
}

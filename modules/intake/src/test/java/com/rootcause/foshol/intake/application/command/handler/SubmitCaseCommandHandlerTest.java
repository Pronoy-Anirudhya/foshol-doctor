package com.rootcause.foshol.intake.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.intake.api.IntakeAudio;
import com.rootcause.foshol.intake.api.IntakeImage;
import com.rootcause.foshol.intake.application.command.CaseSubmissionWriter;
import com.rootcause.foshol.intake.application.command.SubmitCaseCommand;
import com.rootcause.foshol.intake.application.command.SubmitCaseResult;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.application.port.IdempotencyRepository;
import com.rootcause.foshol.intake.application.port.ImageQualityPort;
import com.rootcause.foshol.intake.application.port.ImageTransformPort;
import com.rootcause.foshol.intake.application.port.ObjectStorePort;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import com.rootcause.foshol.intake.domain.QualityReason;
import com.rootcause.foshol.intake.infrastructure.ImageMetricsCalculator;
import com.rootcause.foshol.intake.IntakeFixtures;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubmitCaseCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-07T10:00:00Z");
    private static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-000000000201");
    private static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");

    @Mock
    private DiagnosisCaseRepository cases;

    @Mock
    private IdempotencyRepository keys;

    @Mock
    private ObjectStorePort store;

    @Mock
    private ImageTransformPort transform;

    @Mock
    private FarmerLookupApi farmers;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Mock
    private ApplicationEventPublisher events;

    private SubmitCaseCommandHandler handler;

    @BeforeEach
    void setUp() {
        CaseSubmissionWriter writer = new CaseSubmissionWriter(cases, keys, events);
        handler = new SubmitCaseCommandHandler(
                cases,
                store,
                bytes -> {
                    var metrics = ImageMetricsCalculator.calculate(bytes);
                    return new ImageQualityPort.ImageProbe(
                            metrics.width(),
                            metrics.height(),
                            metrics.blurVariance(),
                            metrics.exposureScore(),
                            metrics.vegetationCoverage());
                },
                transform,
                farmers,
                knowledge,
                writer,
                Clock.fixed(NOW, ZoneOffset.UTC),
                1,
                3,
                8_388_608,
                4_194_304,
                30,
                Duration.ofHours(24),
                20,
                Duration.ofHours(1),
                60.0,
                0.15,
                0.90,
                224,
                0.12,
                1024,
                "image/jpeg,image/png,image/webp",
                "audio/wav,audio/webm,audio/ogg,audio/mp4");
        lenient()
                .when(farmers.findById(FARMER))
                .thenReturn(Optional.of(new FarmerView(FARMER, "Demo", "DHA", "bn", "DHK")));
        lenient()
                .when(knowledge.findCropById(CROP))
                .thenReturn(Optional.of(new CropView(CROP, "rice", "ধান", "Rice", "crop-rice")));
        lenient().when(cases.countByFarmerSince(any(), any())).thenReturn(0L);
        lenient().when(cases.findIdempotency(any())).thenReturn(Optional.empty());
        lenient()
                .when(transform.jpegDerivative(any(), any(Integer.class), any(Integer.class), any(Integer.class)))
                .thenReturn(Optional.empty());
    }

    @Test
    void acceptsSharpJpegAndWav() {
        SubmitCaseResult result = handler.handle(valid());
        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<DiagnosisCase> saved = ArgumentCaptor.forClass(DiagnosisCase.class);
        verify(cases).save(saved.capture());
        assertThat(saved.getValue().districtCode()).isEqualTo("DHA");
        assertThat(saved.getValue().divisionCode()).isEqualTo("DHK");
        ArgumentCaptor<CaseSubmitted> event = ArgumentCaptor.forClass(CaseSubmitted.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().images()).hasSize(1);
        assertThat(event.getValue().audio()).isNotNull();
        verify(cases).upsertFarmerHistory(any(), anyString(), anyString());
    }

    @Test
    void rejectsBlurryImageWithoutStoring() {
        assertThatThrownBy(() -> handler.handle(command(List.of(IntakeFixtures.blurredJpeg()), null)))
                .isInstanceOf(IntakeException.class)
                .satisfies(ex -> {
                    IntakeException intake = (IntakeException) ex;
                    assertThat(intake.errorCode()).isEqualTo(ErrorCodes.ERR_IMAGE_QUALITY_REJECTED);
                    assertThat(intake.status()).isEqualTo(422);
                });
        verify(store, never()).put(anyString(), any(), anyString());
        verify(cases, never()).save(any());
    }

    @Test
    void rejectsLowVegetationWithoutStoring() {
        assertThatThrownBy(() -> handler.handle(command(List.of(IntakeFixtures.nonCropJpeg()), null)))
                .isInstanceOf(IntakeException.class)
                .satisfies(ex -> {
                    IntakeException intake = (IntakeException) ex;
                    assertThat(intake.errorCode()).isEqualTo(ErrorCodes.ERR_IMAGE_QUALITY_REJECTED);
                    assertThat(intake.status()).isEqualTo(422);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errors = (List<Map<String, Object>>) intake.extra();
                    assertThat(errors.getFirst().get("reason")).isEqualTo(QualityReason.NOT_A_CROP.httpReason());
                });
        verify(store, never()).put(anyString(), any(), anyString());
        verify(cases, never()).save(any());
    }

    @Test
    void rejectsMissingFieldMetrics() {
        assertThatThrownBy(() -> handler.handle(command(
                        UUID.randomUUID(),
                        List.of(IntakeFixtures.sharpJpeg()),
                        null,
                        null,
                        null,
                        null,
                        null)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_FIELD_METRICS_INVALID);
        verify(store, never()).put(anyString(), any(), anyString());
    }

    @Test
    void conflictingIdempotencyKeyIs409() {
        UUID key = UUID.randomUUID();
        SubmitCaseResult first = handler.handle(command(key, List.of(IntakeFixtures.sharpJpeg()), null));
        ArgumentCaptor<IdempotencyRecord> saved = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(keys).insert(saved.capture());
        when(cases.findIdempotency(key)).thenReturn(Optional.of(saved.getValue()));
        assertThatThrownBy(() -> handler.handle(command(key, List.of(IntakeFixtures.pngBytes()), null)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_IDEMPOTENCY_KEY_CONFLICT);
        assertThat(first.replayed()).isFalse();
    }

    @Test
    void rateLimitIs429() {
        when(cases.countByFarmerSince(any(), any())).thenReturn(20L);
        assertThatThrownBy(() -> handler.handle(valid()))
                .isInstanceOf(IntakeException.class)
                .satisfies(ex -> {
                    IntakeException intake = (IntakeException) ex;
                    assertThat(intake.errorCode()).isEqualTo(ErrorCodes.ERR_CASE_RATE_LIMITED);
                    assertThat(intake.status()).isEqualTo(429);
                });
        verify(store, never()).put(anyString(), any(), anyString());
    }

    @Test
    void replaysIdenticalIdempotencyKey() {
        UUID key = UUID.randomUUID();
        SubmitCaseResult first = handler.handle(command(key, List.of(IntakeFixtures.sharpJpeg()), null));
        ArgumentCaptor<IdempotencyRecord> saved = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(keys).insert(saved.capture());
        when(cases.findIdempotency(key)).thenReturn(Optional.of(saved.getValue()));
        SubmitCaseResult replayed = handler.handle(command(key, List.of(IntakeFixtures.sharpJpeg()), null));
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.caseId()).isEqualTo(first.caseId());
    }

    @Test
    void conflictsWhenIdempotencyHashDiffers() {
        UUID key = UUID.randomUUID();
        handler.handle(command(key, List.of(IntakeFixtures.sharpJpeg()), null));
        ArgumentCaptor<IdempotencyRecord> saved = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(keys).insert(saved.capture());
        when(cases.findIdempotency(key)).thenReturn(Optional.of(saved.getValue()));
        assertThatThrownBy(() -> handler.handle(command(key, List.of(IntakeFixtures.pngBytes()), null)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    void missingIdempotencyKeyIs400() {
        assertThatThrownBy(() -> handler.handle(command(null, List.of(IntakeFixtures.sharpJpeg()), null)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_IDEMPOTENCY_KEY_MISSING);
    }

    @Test
    void pdfIsUnsupported() {
        assertThatThrownBy(() -> handler.handle(command(List.of(IntakeFixtures.pdf()), null)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_UNSUPPORTED_MEDIA_TYPE);
    }

    private SubmitCaseCommand valid() {
        return command(UUID.randomUUID(), List.of(IntakeFixtures.sharpJpeg()), IntakeFixtures.wav2s());
    }

    private SubmitCaseCommand command(List<byte[]> images, byte[] audio) {
        return command(UUID.randomUUID(), images, audio);
    }

    private SubmitCaseCommand command(UUID key, List<byte[]> images, byte[] audio) {
        return command(key, images, audio, BigDecimal.ONE, FieldAreaUnit.DECIMAL, null, null);
    }

    private SubmitCaseCommand command(
            UUID key,
            List<byte[]> images,
            byte[] audio,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit) {
        List<IntakeImage> parts = images.stream()
                .map(bytes -> new IntakeImage("", "application/octet-stream", bytes))
                .toList();
        IntakeAudio intakeAudio = audio == null ? null : new IntakeAudio("", "audio/wav", audio, 0);
        return new SubmitCaseCommand(
                FARMER,
                CROP,
                null,
                null,
                fieldArea,
                fieldAreaUnit,
                cropQuantity,
                cropQuantityUnit,
                parts,
                intakeAudio,
                key);
    }
}

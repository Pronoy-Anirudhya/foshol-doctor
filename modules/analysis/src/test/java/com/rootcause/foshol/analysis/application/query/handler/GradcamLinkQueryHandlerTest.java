package com.rootcause.foshol.analysis.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.application.config.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.PresignedUrl;
import com.rootcause.foshol.analysis.application.port.AnalysisReadRepository;
import com.rootcause.foshol.analysis.application.query.GradcamLink;
import com.rootcause.foshol.analysis.application.query.GradcamLinkQuery;
import com.rootcause.foshol.analysis.domain.AnalysisException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.intake.api.CaseIntakeApi;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class GradcamLinkQueryHandlerTest {

    private static final UUID CASE_ID = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");
    private static final UUID IMAGE_ID = UUID.fromString("018f0000-0000-7000-8000-0000000000dd");
    private static final UUID CALLER = UUID.fromString("018f0000-0000-7000-8000-0000000000a1");

    @Mock
    private AnalysisReadRepository reads;

    @Mock
    private CaseIntakeApi intake;

    @Mock
    private ObjectStorePort objectStore;

    private GradcamLinkQueryHandler handler() {
        return new GradcamLinkQueryHandler(reads, objectStore, intake, settings());
    }

    private void stubSameDistrict() {
        when(intake.officerSharesDistrict(CASE_ID, CALLER)).thenReturn(true);
    }

    @Test
    void missingOverlayIsEmpty() {
        stubSameDistrict();
        when(reads.findGradcamObjectKey(CASE_ID)).thenReturn(Optional.empty());
        assertThat(handler().handle(new GradcamLinkQuery(CASE_ID, CALLER, Role.OFFICER))).isEmpty();
    }

    @Test
    void returnsPresignedUrl() {
        stubSameDistrict();
        when(reads.findGradcamObjectKey(CASE_ID))
                .thenReturn(Optional.of("cases/x/gradcam/" + IMAGE_ID + ".png"));
        when(reads.findPrimaryGradcamImageId(CASE_ID)).thenReturn(Optional.of(IMAGE_ID));
        Instant expires = Instant.parse("2026-09-07T11:00:00Z");
        when(objectStore.presign(any(), eq(Duration.ofMinutes(10))))
                .thenReturn(new PresignedUrl("http://minio/overlay", expires));
        Optional<GradcamLink> link = handler().handle(new GradcamLinkQuery(CASE_ID, CALLER, Role.OFFICER));
        assertThat(link).isPresent();
        assertThat(link.get().url()).isEqualTo("http://minio/overlay");
        assertThat(link.get().imageId()).isEqualTo(IMAGE_ID);
        assertThat(link.get().expiresAt()).isEqualTo(expires);
    }

    @Test
    void storageFailureIs503() {
        stubSameDistrict();
        when(reads.findGradcamObjectKey(CASE_ID)).thenReturn(Optional.of("cases/x/gradcam/" + IMAGE_ID + ".png"));
        when(objectStore.presign(any(), any())).thenThrow(new IllegalStateException("down"));
        assertThatThrownBy(() -> handler().handle(new GradcamLinkQuery(CASE_ID, CALLER, Role.OFFICER)))
                .isInstanceOf(AnalysisException.class)
                .extracting(ex -> ((AnalysisException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_STORAGE_UNAVAILABLE);
    }

    private static AnalysisSettings settings() {
        return new AnalysisSettings(
                "replay",
                new BigDecimal("0.75"),
                new BigDecimal("0.45"),
                BigDecimal.ONE,
                "MAX",
                Duration.ofSeconds(3),
                5,
                true,
                "http://localhost:8000",
                Duration.ofSeconds(8),
                Duration.ofMinutes(10),
                "http://localhost:9000",
                "minio",
                "minio12345",
                "foshol-cases");
    }
}

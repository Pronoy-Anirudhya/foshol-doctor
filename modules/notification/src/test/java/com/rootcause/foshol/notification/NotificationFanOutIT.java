package com.rootcause.foshol.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.common.enums.RejectionReason;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.AdvisoryRevised;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.notification.api.NotificationChannel;
import com.rootcause.foshol.notification.application.command.handler.AdvisoryApprovedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.AdvisoryRevisedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.CaseRejectedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.CaseStatusChangedEventHandler;
import com.rootcause.foshol.notification.domain.DeliveryState;
import com.rootcause.foshol.notification.infrastructure.channel.SmsChannel;
import com.rootcause.foshol.notification.infrastructure.channel.WebPushChannel;
import com.rootcause.foshol.notification.infrastructure.sse.SseSubscriptionRegistry;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.api.ReviewSubmissionApi;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = NotificationModuleTestApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@Import(NotificationFanOutIT.Stubs.class)
class NotificationFanOutIT {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    private static final UUID DISEASE = UUID.fromString("01800000-0000-7000-8000-000000000101");
    private static final UUID F1 = UUID.fromString("01800000-0000-7000-8000-00000000f001");
    private static final UUID F2 = UUID.fromString("01800000-0000-7000-8000-00000000f002");
    private static final UUID OFFICER = UUID.fromString("01800000-0000-7000-8000-00000000c001");
    private static final UUID CASE = UUID.fromString("01800000-0000-7000-8000-00000000a001");
    private static final UUID ADVISORY_V1 = UUID.fromString("01800000-0000-7000-8000-00000000d001");
    private static final UUID ADVISORY_V2 = UUID.fromString("01800000-0000-7000-8000-00000000d002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("foshol")
            .withUsername("foshol")
            .withPassword("foshol");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("foshol.channels.sse.enabled", () -> "true");
        registry.add("foshol.channels.webpush.enabled", () -> "false");
        registry.add("foshol.channels.sms.enabled", () -> "false");
        registry.add("foshol.channels.sse.heartbeat", () -> "PT20S");
        registry.add("foshol.channels.sse.timeout", () -> "PT30M");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Autowired
    private CaseStatusChangedEventHandler status;

    @Autowired
    private AdvisoryApprovedEventHandler approved;

    @Autowired
    private AdvisoryRevisedEventHandler revised;

    @Autowired
    private CaseRejectedEventHandler rejected;

    @Autowired
    private SseSubscriptionRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<NotificationChannel> channels;

    @MockitoSpyBean
    private WebPushChannel webPush;

    @MockitoSpyBean
    private SmsChannel sms;

    @Test
    void fanOutAndIdempotency() {
        seed();
        CapturingEmitter farmer = new CapturingEmitter();
        CapturingEmitter officer = new CapturingEmitter();
        CapturingEmitter other = new CapturingEmitter();
        registry.attach(F1, Role.FARMER, null, null, farmer);
        registry.attach(OFFICER, Role.OFFICER, "DHA", null, officer);
        registry.attach(F2, Role.FARMER, null, null, other);

        status.handle(new CaseStatusChanged(CASE, F1, CaseStatus.ANALYSING, CaseStatus.ANALYSED, "c1", T0));
        assertThat(countRows()).isEqualTo(1);
        assertThat(farmer.payloads.toString()).contains("case-status");
        assertThat(officer.payloads.toString()).contains("queue");
        assertThat(other.payloads).isEmpty();

        approved.handle(new AdvisoryApproved(
                ADVISORY_V1, CASE, F1, OFFICER, "Officer A", DISEASE, "d-name", AdvisoryAction.APPROVED, 1, "c2", T0));
        assertThat(countRows()).isEqualTo(2);
        assertThat(stateOf(NotificationType.ADVISORY_PUBLISHED)).isEqualTo(DeliveryState.SENT.name());
        assertThat(farmer.payloads.toString()).contains("advisory");
        assertThat(farmer.payloads.toString()).contains("d-name");
        assertThat(other.payloads).isEmpty();

        approved.handle(new AdvisoryApproved(
                ADVISORY_V1, CASE, F1, OFFICER, "Officer A", DISEASE, "d-name", AdvisoryAction.APPROVED, 1, "c2", T0));
        assertThat(countRows()).isEqualTo(2);

        revised.handle(new AdvisoryRevised(ADVISORY_V2, ADVISORY_V1, CASE, F1, OFFICER, "Officer A", 2, "c3", T0));
        assertThat(countRows()).isEqualTo(3);

        registry.attach(F1, Role.FARMER, null, null, new CapturingEmitter()).emitter().complete();
        // disconnect F1's live connections by completing them
        farmer.complete();
        assertThat(registry.farmerConnected(F1)).isFalse();
        rejected.handle(new CaseRejected(
                CASE, F1, OFFICER, "Officer A", RejectionReason.BLURRY_IMAGE, NotifyFixtures.FIXTURE_MESSAGE, "c4", T0));
        assertThat(stateOf(NotificationType.CASE_REJECTED)).isEqualTo(DeliveryState.SKIPPED.name());
        assertThat(jdbc.queryForObject(
                        "select attempts from notification where type = 'CASE_REJECTED'", Short.class))
                .isZero();

        CapturingEmitter reconnect = new CapturingEmitter();
        registry.attach(F1, Role.FARMER, null, ADVISORY_V1.toString(), reconnect);
        assertThat(reconnect.payloads.getFirst()).contains("resync");

        assertThat(channels).hasSize(3);
        verify(webPush, never()).send(any());
        verify(sms, never()).send(any());
    }

    private void seed() {
        jdbc.update(
                "insert into farmer (id, name, phone_hash, phone_enc, district_code, division_code, preferred_language) values (?,?,?,?,?,?,?) on conflict do nothing",
                F1,
                "Farmer 1",
                "1".repeat(64),
                new byte[] {1},
                "DHA",
                "DHK",
                "bn");
        jdbc.update(
                "insert into farmer (id, name, phone_hash, phone_enc, district_code, division_code, preferred_language) values (?,?,?,?,?,?,?) on conflict do nothing",
                F2,
                "Farmer 2",
                "2".repeat(64),
                new byte[] {1},
                "DHA",
                "DHK",
                "bn");
        jdbc.update(
                "insert into field_officer (id, name, username, password_hash, phone_hash, phone_enc, district_code, division_code, role, active) values (?,?,?,?,?,?,?,?,?,true) on conflict do nothing",
                OFFICER,
                "Officer A",
                "officer.a",
                "hash",
                "3".repeat(64),
                new byte[] {1},
                "DHA",
                "DHK",
                "OFFICER");
        jdbc.update(
                "insert into diagnosis_case (id, farmer_id, crop_id, status, district_code, division_code, correlation_id) values (?,?,?,?,?,?,?) on conflict do nothing",
                CASE,
                F1,
                CROP,
                "ANALYSED",
                "DHA",
                "DHK",
                "c1");
        jdbc.update(
                """
                insert into advisory (id, case_id, disease_id, officer_id, action, version, published_at, created_by, updated_by)
                values (?, ?, ?, ?, 'APPROVED', 1, now(), 'system', 'system')
                on conflict do nothing
                """,
                ADVISORY_V1,
                CASE,
                DISEASE,
                OFFICER);
        jdbc.update(
                """
                insert into advisory (id, case_id, disease_id, officer_id, action, version, supersedes_id, published_at, created_by, updated_by)
                values (?, ?, ?, ?, 'EDITED', 2, ?, now(), 'system', 'system')
                on conflict do nothing
                """,
                ADVISORY_V2,
                CASE,
                DISEASE,
                OFFICER,
                ADVISORY_V1);
    }

    private long countRows() {
        Long n = jdbc.queryForObject("select count(*) from notification", Long.class);
        return n == null ? 0 : n;
    }

    private String stateOf(NotificationType type) {
        return jdbc.queryForObject("select state from notification where type = ?", String.class, type.name());
    }

    static final class CapturingEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        CapturingEmitter() {
            super(Long.MAX_VALUE);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(part -> payloads.add(String.valueOf(part.getData())));
        }
    }

    @TestConfiguration
    static class Stubs {
        @Bean
        @Primary
        FarmerLookupApi farmerLookupApi() {
            return new FarmerLookupApi() {
                @Override
                public Optional<FarmerView> findById(UUID farmerId) {
                    if (F1.equals(farmerId)) {
                        return Optional.of(new FarmerView(F1, "Farmer 1", "DHA", "bn", "DHK"));
                    }
                    if (F2.equals(farmerId)) {
                        return Optional.of(new FarmerView(F2, "Farmer 2", "DHA", "bn", "DHK"));
                    }
                    return Optional.empty();
                }

                @Override
                public Optional<FarmerView> findByPhone(String e164Phone) {
                    return Optional.empty();
                }
            };
        }

        @Bean
        @Primary
        ReviewSubmissionApi reviewSubmissionApi() {
            return new ReviewSubmissionApi() {
                @Override
                public Optional<AdvisoryView> findPublishedAdvisory(UUID caseId) {
                    return Optional.of(NotifyFixtures.advisoryView());
                }

                @Override
                public List<AdvisoryView> findAdvisoryHistory(UUID caseId) {
                    return List.of();
                }

                @Override
                public Optional<RejectionView> findRejection(UUID caseId) {
                    return Optional.of(NotifyFixtures.rejectionView());
                }
            };
        }
    }
}

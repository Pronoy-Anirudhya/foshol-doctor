package com.rootcause.foshol.notification.application.command;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.notification.application.DeliveryService;
import com.rootcause.foshol.notification.application.NotificationContentAssembler;
import com.rootcause.foshol.notification.application.NotificationRepository;
import com.rootcause.foshol.notification.domain.Notification;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.ReviewSubmissionApi;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HandleAdvisoryApproved {

    private static final Logger log = LoggerFactory.getLogger(HandleAdvisoryApproved.class);

    private final FarmerLookupApi farmers;
    private final ReviewSubmissionApi review;
    private final NotificationRepository notifications;
    private final NotificationContentAssembler assembler;
    private final DeliveryService delivery;
    private final Clock clock;

    public HandleAdvisoryApproved(
            FarmerLookupApi farmers,
            ReviewSubmissionApi review,
            NotificationRepository notifications,
            NotificationContentAssembler assembler,
            DeliveryService delivery,
            Clock clock) {
        this.farmers = farmers;
        this.review = review;
        this.notifications = notifications;
        this.assembler = assembler;
        this.delivery = delivery;
        this.clock = clock;
    }

    public void handle(AdvisoryApproved event) {
        CorrelationId.set(event.correlationId());
        Optional<FarmerView> farmer = farmers.findById(event.farmerId());
        if (farmer.isEmpty()) {
            log.warn("Unknown farmer on AdvisoryApproved correlationId={}", event.correlationId());
            return;
        }
        if (notifications
                .findDuplicate(
                        event.farmerId(),
                        event.caseId(),
                        NotificationType.ADVISORY_PUBLISHED,
                        event.advisoryId().toString())
                .isPresent()) {
            return;
        }
        AdvisoryView advisory = review.findPublishedAdvisory(event.caseId()).orElse(null);
        NotificationContentAssembler.Assembled content;
        if (advisory != null) {
            content = assembler.assembleAdvisory(NotificationType.ADVISORY_PUBLISHED, advisory, farmer.get());
        } else {
            content = assembler.assemble(
                    NotificationContentAssembler.KEY_ADVISORY_PUBLISHED,
                    farmer.get().preferredLanguage(),
                    Map.of(
                            "diseaseNameBn", nullToEmpty(event.diseaseNameBn()),
                            "officerName", nullToEmpty(event.officerName()),
                            "remedyCount", "0"));
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("correlationId", event.correlationId());
        payload.put("advisoryId", event.advisoryId().toString());
        delivery.deliver(Notification.pending(
                Uuid7.create(),
                event.farmerId(),
                event.caseId(),
                event.advisoryId(),
                NotificationType.ADVISORY_PUBLISHED,
                content.titleBn(),
                content.bodyBn(),
                payload,
                clock.instant()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.rootcause.foshol.notification.application.command;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.notification.api.NotificationChannel;
import com.rootcause.foshol.notification.api.NotificationPort;
import com.rootcause.foshol.notification.application.port.NotificationRepository;
import com.rootcause.foshol.notification.domain.Notification;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class DeliveryService implements NotificationPort {

    private final NotificationRepository notifications;
    private final List<NotificationChannel> channels;
    private final Clock clock;
    private final TransactionTemplate tx;

    public DeliveryService(
            NotificationRepository notifications,
            List<NotificationChannel> channels,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.notifications = notifications;
        this.channels = channels.stream().sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void publish(AdvisoryNotification notification) {
        Map<String, String> payload = new LinkedHashMap<>(notification.data());
        payload.putIfAbsent("correlationId", CorrelationId.currentOrCreate());
        deliver(Notification.pending(
                notification.notificationId(),
                notification.farmerId(),
                notification.caseId(),
                notification.advisoryId(),
                notification.type(),
                notification.titleBn(),
                notification.bodyBn(),
                payload,
                clock.instant()));
    }

    public void deliver(Notification pending) {
        Boolean duplicate = tx.execute(status -> {
            if (notifications
                    .findDuplicate(pending.farmerId(), pending.caseId(), pending.type(), dedupeKey(pending))
                    .isPresent()) {
                return true;
            }
            notifications.insert(pending);
            return false;
        });
        if (Boolean.TRUE.equals(duplicate)) {
            return;
        }
        short attempts = 0;
        boolean anyConsulted = false;
        for (NotificationChannel channel : channels) {
            if (!channel.enabled() || !channel.supports(pending.farmerId())) {
                continue;
            }
            anyConsulted = true;
            attempts++;
            boolean sent;
            try {
                sent = channel.send(toApi(pending));
            } catch (RuntimeException ex) {
                log.warn("Channel {} threw for case {}", channel.name(), pending.caseId(), ex);
                sent = false;
            }
            if (sent) {
                pending.markSent(channel.name(), clock.instant(), attempts);
                tx.executeWithoutResult(status -> notifications.update(pending));
                log.info(
                        "notification sent type={} caseId={} channel={}",
                        pending.type(),
                        pending.caseId(),
                        channel.name());
                return;
            }
        }
        if (!anyConsulted) {
            pending.markSkipped();
        } else {
            pending.markFailed(attempts);
        }
        pending.ensureNotPending(clock.instant());
        tx.executeWithoutResult(status -> notifications.update(pending));
    }

    public List<NotificationChannel> orderedChannels() {
        return channels;
    }

    private static String dedupeKey(Notification pending) {
        return switch (pending.type()) {
            case ADVISORY_PUBLISHED, ADVISORY_REVISED -> pending.advisoryId().toString();
            case CASE_STATUS_CHANGED -> pending.payload().getOrDefault("toStatus", "");
            case CASE_REJECTED -> pending.caseId().toString();
        };
    }

    private static AdvisoryNotification toApi(Notification n) {
        return new AdvisoryNotification(
                n.id(), n.farmerId(), n.caseId(), n.advisoryId(), n.type(), n.titleBn(), n.bodyBn(), n.payload());
    }
}

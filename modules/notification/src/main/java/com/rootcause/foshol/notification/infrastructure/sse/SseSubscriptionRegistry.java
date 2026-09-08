package com.rootcause.foshol.notification.infrastructure.sse;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.notification.application.OfficerQueueNudgePort;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseSubscriptionRegistry implements OfficerQueueNudgePort {

    public static final String EVENT_ADVISORY = "advisory";
    public static final String EVENT_CASE_STATUS = "case-status";
    public static final String EVENT_QUEUE = "queue";
    public static final String EVENT_RESYNC = "resync";
    public static final String EVENT_RECONNECT = "reconnect";

    private static final int MAX_PER_SUBJECT = 5;
    private static final Logger log = LoggerFactory.getLogger(SseSubscriptionRegistry.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseSubscription>> bySubject = new ConcurrentHashMap<>();
    private final Clock clock;

    public SseSubscriptionRegistry(Clock clock) {
        this.clock = clock;
    }

    public SseEmitter subscribe(UUID subjectId, Role role, String districtCode, String lastEventId, Duration timeout) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        attach(subjectId, role, districtCode, lastEventId, emitter);
        return emitter;
    }

    public SseSubscription attach(UUID subjectId, Role role, String districtCode, String lastEventId, SseEmitter emitter) {
        if (lastEventId != null && !lastEventId.isBlank()) {
            log.debug("Last-Event-ID {} for subject {}", lastEventId, subjectId);
        }
        SseSubscription sub = new SseSubscription(subjectId, role, districtCode, emitter, clock.instant(), lastEventId);
        CopyOnWriteArrayList<SseSubscription> list =
                bySubject.computeIfAbsent(subjectId, id -> new CopyOnWriteArrayList<>());
        list.add(sub);
        while (list.size() > MAX_PER_SUBJECT) {
            SseSubscription oldest = list.remove(0);
            oldest.emitter().complete();
            log.debug("Closed oldest SSE subscription for {}", subjectId);
        }
        emitter.onCompletion(() -> remove(sub));
        emitter.onTimeout(() -> {
            try {
                emitter.send(SseEmitter.event().name(EVENT_RECONNECT).data("{}", MediaType.APPLICATION_JSON));
            } catch (IOException ignored) {
                // closing anyway
            }
            emitter.complete();
            remove(sub);
        });
        emitter.onError(ex -> remove(sub));
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_RESYNC).data("{}", MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                remove(sub);
            }
        }
        return sub;
    }

    public void heartbeat() {
        for (SseSubscription sub : all()) {
            try {
                sub.emitter().send(SseEmitter.event().comment("heartbeat").id(Long.toString(sub.nextId())));
            } catch (IOException ex) {
                remove(sub);
            }
        }
    }

    public boolean sendToFarmer(AdvisoryNotification notification) {
        List<SseSubscription> targets = bySubject.getOrDefault(notification.farmerId(), new CopyOnWriteArrayList<>());
        boolean any = false;
        String eventName = notification.type() == NotificationType.CASE_STATUS_CHANGED
                ? EVENT_CASE_STATUS
                : EVENT_ADVISORY;
        Map<String, String> data = frame(notification);
        for (SseSubscription sub : targets) {
            if (sub.role() != Role.FARMER) {
                continue;
            }
            try {
                sub.emitter()
                        .send(SseEmitter.event()
                                .name(eventName)
                                .id(notification.notificationId().toString())
                                .data(data, MediaType.APPLICATION_JSON));
                any = true;
            } catch (IOException ex) {
                remove(sub);
            }
        }
        return any;
    }

    @Override
    public void emitQueue(UUID caseId, String toStatus, String correlationId, String districtCode) {
        Map<String, String> data = Map.of(
                "caseId", caseId.toString(), "toStatus", toStatus, "correlationId", correlationId);
        String wanted = districtCode == null ? "" : districtCode;
        for (SseSubscription sub : all()) {
            if (sub.role() != Role.OFFICER && sub.role() != Role.ADMIN) {
                continue;
            }
            if (!wanted.isBlank() && sub.districtCode() != null && !wanted.equals(sub.districtCode())) {
                continue;
            }
            try {
                sub.emitter()
                        .send(SseEmitter.event()
                                .name(EVENT_QUEUE)
                                .id(Long.toString(sub.nextId()))
                                .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                remove(sub);
            }
        }
    }

    @Override
    public boolean farmerConnected(UUID farmerId) {
        return bySubject.getOrDefault(farmerId, new CopyOnWriteArrayList<>()).stream()
                .anyMatch(s -> s.role() == Role.FARMER);
    }

    public int count(UUID subjectId) {
        return bySubject.getOrDefault(subjectId, new CopyOnWriteArrayList<>()).size();
    }

    void remove(SseSubscription sub) {
        CopyOnWriteArrayList<SseSubscription> list = bySubject.get(sub.subjectId());
        if (list != null) {
            list.remove(sub);
            if (list.isEmpty()) {
                bySubject.remove(sub.subjectId());
            }
        }
    }

    private List<SseSubscription> all() {
        List<SseSubscription> out = new ArrayList<>();
        bySubject.values().forEach(out::addAll);
        return out;
    }

    private static Map<String, String> frame(AdvisoryNotification notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", notification.notificationId().toString());
        data.put("caseId", notification.caseId().toString());
        data.put("correlationId", notification.data().getOrDefault("correlationId", ""));
        if (notification.type() == NotificationType.CASE_STATUS_CHANGED) {
            data.put("fromStatus", notification.data().getOrDefault("fromStatus", ""));
            data.put("toStatus", notification.data().getOrDefault("toStatus", ""));
            return data;
        }
        if (notification.advisoryId() != null) {
            data.put("advisoryId", notification.advisoryId().toString());
        }
        data.put("type", notification.type().name());
        data.put("titleBn", notification.titleBn());
        data.put("bodyBn", notification.bodyBn());
        return data;
    }

    public static final class SseSubscription {
        private final UUID subjectId;
        private final Role role;
        private final String districtCode;
        private final SseEmitter emitter;
        private final Instant connectedAt;
        private final String lastEventId;
        private final AtomicLong counter = new AtomicLong();

        SseSubscription(
                UUID subjectId,
                Role role,
                String districtCode,
                SseEmitter emitter,
                Instant connectedAt,
                String lastEventId) {
            this.subjectId = subjectId;
            this.role = role;
            this.districtCode = districtCode;
            this.emitter = emitter;
            this.connectedAt = connectedAt;
            this.lastEventId = lastEventId;
        }

        public UUID subjectId() {
            return subjectId;
        }

        public Role role() {
            return role;
        }

        public String districtCode() {
            return districtCode;
        }

        public SseEmitter emitter() {
            return emitter;
        }

        public Instant connectedAt() {
            return connectedAt;
        }

        public String lastEventId() {
            return lastEventId;
        }

        long nextId() {
            return counter.incrementAndGet();
        }
    }
}

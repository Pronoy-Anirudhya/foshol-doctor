package com.rootcause.foshol.notification.domain;

import com.rootcause.foshol.common.BanglaNormalizer;
import com.rootcause.foshol.common.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class Notification {

    private final UUID id;
    private final UUID farmerId;
    private final UUID caseId;
    private UUID advisoryId;
    private String channel;
    private final NotificationType type;
    private final String titleBn;
    private final String bodyBn;
    private final Map<String, String> payload;
    private DeliveryState state;
    private short attempts;
    private Instant deliveredAt;
    private final Instant createdAt;

    public Notification(
            UUID id,
            UUID farmerId,
            UUID caseId,
            UUID advisoryId,
            String channel,
            NotificationType type,
            String titleBn,
            String bodyBn,
            Map<String, String> payload,
            DeliveryState state,
            short attempts,
            Instant deliveredAt,
            Instant createdAt) {
        if ((state == DeliveryState.SENT) != (deliveredAt != null)) {
            throw new IllegalArgumentException("deliveredAt is set if and only if state is SENT.");
        }
        if (state == DeliveryState.SKIPPED && attempts != 0) {
            throw new IllegalArgumentException("SKIPPED implies attempts = 0.");
        }
        if (titleBn == null || titleBn.isBlank() || bodyBn == null || bodyBn.isBlank()) {
            throw new IllegalArgumentException("title and body are required.");
        }
        if ((type == NotificationType.ADVISORY_PUBLISHED || type == NotificationType.ADVISORY_REVISED)
                && advisoryId == null) {
            throw new IllegalArgumentException("advisoryId is required for advisory notifications.");
        }
        this.id = id;
        this.farmerId = farmerId;
        this.caseId = caseId;
        this.advisoryId = advisoryId;
        this.channel = channel;
        this.type = type;
        this.titleBn = BanglaNormalizer.forStorage(titleBn);
        this.bodyBn = BanglaNormalizer.forStorage(bodyBn);
        this.payload = Map.copyOf(payload);
        this.state = state;
        this.attempts = attempts;
        this.deliveredAt = deliveredAt;
        this.createdAt = createdAt;
    }

    public static Notification pending(
            UUID id,
            UUID farmerId,
            UUID caseId,
            UUID advisoryId,
            NotificationType type,
            String titleBn,
            String bodyBn,
            Map<String, String> payload,
            Instant createdAt) {
        return new Notification(
                id,
                farmerId,
                caseId,
                advisoryId,
                ChannelNames.SSE,
                type,
                titleBn,
                bodyBn,
                payload,
                DeliveryState.PENDING,
                (short) 0,
                null,
                createdAt);
    }

    public void markSent(String channel, Instant deliveredAt, short attempts) {
        requirePending();
        this.channel = channel;
        this.state = DeliveryState.SENT;
        this.deliveredAt = deliveredAt;
        this.attempts = (short) Math.max(1, attempts);
    }

    public void markSent(String channel, Instant deliveredAt) {
        markSent(channel, deliveredAt, (short) 1);
    }

    public void markFailed(short attempts) {
        requirePending();
        this.state = DeliveryState.FAILED;
        this.attempts = attempts;
        this.deliveredAt = null;
    }

    public void markSkipped() {
        requirePending();
        this.channel = ChannelNames.SSE;
        this.state = DeliveryState.SKIPPED;
        this.attempts = 0;
        this.deliveredAt = null;
    }

    public void ensureNotPending(Instant now) {
        if (state == DeliveryState.PENDING) {
            markFailed(attempts);
        }
    }

    private void requirePending() {
        if (state != DeliveryState.PENDING) {
            throw new IllegalStateException("Delivery state is already terminal.");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID farmerId() {
        return farmerId;
    }

    public UUID caseId() {
        return caseId;
    }

    public UUID advisoryId() {
        return advisoryId;
    }

    public String channel() {
        return channel;
    }

    public NotificationType type() {
        return type;
    }

    public String titleBn() {
        return titleBn;
    }

    public String bodyBn() {
        return bodyBn;
    }

    public Map<String, String> payload() {
        return payload;
    }

    public DeliveryState state() {
        return state;
    }

    public short attempts() {
        return attempts;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

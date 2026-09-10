package com.rootcause.foshol.notification.infrastructure.persistence;

import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.notification.application.port.NotificationRepository;
import com.rootcause.foshol.notification.domain.Notification;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(Notification notification) {
        jpa.saveAndFlush(toEntity(notification));
    }

    @Override
    public void update(Notification notification) {
        NotificationEntity entity = jpa.findById(notification.id()).orElseGet(() -> toEntity(notification));
        entity.setChannel(notification.channel());
        entity.setState(notification.state());
        entity.setAttempts(notification.attempts());
        entity.setDeliveredAt(notification.deliveredAt());
        entity.setPayload(Map.copyOf(notification.payload()));
        jpa.saveAndFlush(entity);
    }

    @Override
    public Optional<Notification> findDuplicate(UUID farmerId, UUID caseId, NotificationType type, String dedupeKey) {
        if (type == NotificationType.ADVISORY_PUBLISHED || type == NotificationType.ADVISORY_REVISED) {
            return jpa.findFirstByFarmerIdAndCaseIdAndTypeAndAdvisoryId(
                            farmerId, caseId, type, UUID.fromString(dedupeKey))
                    .map(this::toDomain);
        }
        return jpa.findByFarmerIdAndCaseIdAndType(farmerId, caseId, type).stream()
                .filter(entity -> type != NotificationType.CASE_STATUS_CHANGED
                        || dedupeKey.equals(
                                entity.getPayload() == null ? null : entity.getPayload().get("toStatus")))
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public List<Notification> findByFarmer(UUID farmerId, int page, int size) {
        return jpa.findByFarmerIdOrderByCreatedAtDesc(farmerId, PageRequest.of(page, size)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByFarmer(UUID farmerId) {
        return jpa.countByFarmerId(farmerId);
    }

    private NotificationEntity toEntity(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setId(n.id());
        e.setFarmerId(n.farmerId());
        e.setCaseId(n.caseId());
        e.setAdvisoryId(n.advisoryId());
        e.setChannel(n.channel());
        e.setType(n.type());
        e.setTitleBn(n.titleBn());
        e.setBodyBn(n.bodyBn());
        e.setPayload(Map.copyOf(n.payload()));
        e.setState(n.state());
        e.setAttempts(n.attempts());
        e.setDeliveredAt(n.deliveredAt());
        e.setCreatedAt(n.createdAt());
        return e;
    }

    private Notification toDomain(NotificationEntity e) {
        Map<String, String> payload = e.getPayload() == null ? Map.of() : Map.copyOf(e.getPayload());
        return new Notification(
                e.getId(),
                e.getFarmerId(),
                e.getCaseId(),
                e.getAdvisoryId(),
                e.getChannel(),
                e.getType(),
                e.getTitleBn(),
                e.getBodyBn(),
                payload,
                e.getState(),
                e.getAttempts(),
                e.getDeliveredAt(),
                e.getCreatedAt());
    }
}

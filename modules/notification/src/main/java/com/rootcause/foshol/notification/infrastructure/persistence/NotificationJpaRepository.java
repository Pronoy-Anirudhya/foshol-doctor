package com.rootcause.foshol.notification.infrastructure.persistence;

import com.rootcause.foshol.common.enums.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByFarmerIdOrderByCreatedAtDesc(UUID farmerId, Pageable pageable);

    long countByFarmerId(UUID farmerId);

    Optional<NotificationEntity> findFirstByFarmerIdAndCaseIdAndTypeAndAdvisoryId(
            UUID farmerId, UUID caseId, NotificationType type, UUID advisoryId);

    List<NotificationEntity> findByFarmerIdAndCaseIdAndType(UUID farmerId, UUID caseId, NotificationType type);
}

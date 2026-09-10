package com.rootcause.foshol.review.infrastructure.persistence;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.enums.RejectionReason;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import com.rootcause.foshol.review.domain.CaseRejection;
import java.util.List;

public final class AdvisoryMapper {

    private AdvisoryMapper() {}

    public static AdvisoryEntity toNewEntity(Advisory advisory) {
        AdvisoryEntity entity = new AdvisoryEntity();
        entity.setId(advisory.id());
        entity.setCaseId(advisory.caseId());
        entity.setDiseaseId(advisory.diseaseId());
        entity.setOfficerId(advisory.officerId());
        entity.setAction(advisory.action().name());
        entity.setOfficerNoteBn(advisory.officerNoteBn());
        entity.setVersion(advisory.version());
        entity.setSupersedesId(advisory.supersedesId());
        entity.setPublishedAt(advisory.publishedAt());
        entity.setCreatedAt(advisory.publishedAt());
        entity.setCreatedBy(advisory.createdBy());
        entity.setUpdatedAt(advisory.publishedAt());
        entity.setUpdatedBy(advisory.createdBy());
        return entity;
    }

    public static Advisory toDomain(AdvisoryEntity entity, List<AdvisoryRemedy> remedies) {
        return new Advisory(
                entity.getId(),
                entity.getCaseId(),
                entity.getDiseaseId(),
                entity.getOfficerId(),
                AdvisoryAction.valueOf(entity.getAction()),
                entity.getOfficerNoteBn(),
                entity.getVersion(),
                entity.getSupersedesId(),
                remedies,
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getCreatedBy());
    }

    public static AdvisoryRemedy toDomain(AdvisoryRemedyEntity entity) {
        return new AdvisoryRemedy(entity.getRemedyId(), entity.getDisplayOrder());
    }

    public static CaseRejectionEntity toNewEntity(CaseRejection rejection) {
        CaseRejectionEntity entity = new CaseRejectionEntity();
        entity.setId(rejection.id());
        entity.setCaseId(rejection.caseId());
        entity.setOfficerId(rejection.officerId());
        entity.setReasonCode(rejection.reasonCode().name());
        entity.setMessageBn(rejection.messageBn());
        entity.setCreatedAt(rejection.createdAt());
        return entity;
    }

    public static CaseRejection toDomain(CaseRejectionEntity entity) {
        return new CaseRejection(
                entity.getId(),
                entity.getCaseId(),
                entity.getOfficerId(),
                RejectionReason.valueOf(entity.getReasonCode()),
                entity.getMessageBn(),
                entity.getCreatedAt());
    }
}

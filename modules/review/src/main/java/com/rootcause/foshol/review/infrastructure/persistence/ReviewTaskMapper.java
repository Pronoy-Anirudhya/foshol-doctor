package com.rootcause.foshol.review.infrastructure.persistence;

import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.review.domain.ReviewTask;

public final class ReviewTaskMapper {

    private ReviewTaskMapper() {}

    public static ReviewTask toDomain(ReviewTaskEntity entity) {
        return new ReviewTask(
                entity.getId(),
                entity.getCaseId(),
                entity.getOfficerId(),
                ReviewState.valueOf(entity.getState()),
                entity.getPriorityConfidence(),
                entity.getClaimedAt(),
                entity.getSlaDueAt(),
                entity.getAssignmentOpenedAt(),
                entity.getAssignmentDueAt(),
                entity.getResolutionDueAt(),
                entity.getKpiWarnEmittedAt(),
                entity.getRequeueCount(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    public static void copy(ReviewTask task, ReviewTaskEntity entity) {
        entity.setId(task.id());
        entity.setCaseId(task.caseId());
        entity.setOfficerId(task.officerId());
        entity.setState(task.state().name());
        entity.setPriorityConfidence(task.priorityConfidence());
        entity.setClaimedAt(task.claimedAt());
        entity.setSlaDueAt(task.slaDueAt());
        entity.setAssignmentOpenedAt(task.assignmentOpenedAt());
        entity.setAssignmentDueAt(task.assignmentDueAt());
        entity.setResolutionDueAt(task.resolutionDueAt());
        entity.setKpiWarnEmittedAt(task.kpiWarnEmittedAt());
        entity.setRequeueCount(task.requeueCount());
        entity.setCreatedAt(task.createdAt());
        entity.setCreatedBy(task.createdBy());
        entity.setUpdatedAt(task.updatedAt());
        entity.setUpdatedBy(task.updatedBy());
    }

    public static ReviewTaskEntity toNewEntity(ReviewTask task) {
        ReviewTaskEntity entity = new ReviewTaskEntity();
        copy(task, entity);
        entity.setVersion(task.version());
        return entity;
    }
}

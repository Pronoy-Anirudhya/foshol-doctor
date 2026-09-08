package com.rootcause.foshol.review.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_task")
public class ReviewTaskEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true)
    private UUID caseId;

    @Column(name = "officer_id")
    private UUID officerId;

    @Column(nullable = false, length = 12)
    private String state;

    @Column(name = "priority_confidence", precision = 5, scale = 4)
    private BigDecimal priorityConfidence;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "assignment_opened_at", nullable = false)
    private Instant assignmentOpenedAt;

    @Column(name = "assignment_due_at", nullable = false)
    private Instant assignmentDueAt;

    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @Column(name = "kpi_warn_emitted_at")
    private Instant kpiWarnEmittedAt;

    @Column(name = "requeue_count", nullable = false)
    private short requeueCount;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 60)
    private String updatedBy;

    public ReviewTaskEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getOfficerId() {
        return officerId;
    }

    public void setOfficerId(UUID officerId) {
        this.officerId = officerId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public BigDecimal getPriorityConfidence() {
        return priorityConfidence;
    }

    public void setPriorityConfidence(BigDecimal priorityConfidence) {
        this.priorityConfidence = priorityConfidence;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(Instant slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public Instant getAssignmentOpenedAt() {
        return assignmentOpenedAt;
    }

    public void setAssignmentOpenedAt(Instant assignmentOpenedAt) {
        this.assignmentOpenedAt = assignmentOpenedAt;
    }

    public Instant getAssignmentDueAt() {
        return assignmentDueAt;
    }

    public void setAssignmentDueAt(Instant assignmentDueAt) {
        this.assignmentDueAt = assignmentDueAt;
    }

    public Instant getResolutionDueAt() {
        return resolutionDueAt;
    }

    public void setResolutionDueAt(Instant resolutionDueAt) {
        this.resolutionDueAt = resolutionDueAt;
    }

    public Instant getKpiWarnEmittedAt() {
        return kpiWarnEmittedAt;
    }

    public void setKpiWarnEmittedAt(Instant kpiWarnEmittedAt) {
        this.kpiWarnEmittedAt = kpiWarnEmittedAt;
    }

    public short getRequeueCount() {
        return requeueCount;
    }

    public void setRequeueCount(short requeueCount) {
        this.requeueCount = requeueCount;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

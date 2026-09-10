package com.rootcause.foshol.review.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_rejection")
public class CaseRejectionEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true)
    private UUID caseId;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @Column(name = "reason_code", nullable = false, length = 32)
    private String reasonCode;

    @Column(name = "message_bn", nullable = false)
    private String messageBn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseRejectionEntity() {}

    public CaseRejectionEntity(
            UUID id,
            UUID caseId,
            UUID officerId,
            com.rootcause.foshol.common.enums.RejectionReason reasonCode,
            String messageBn,
            Instant createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.officerId = officerId;
        this.reasonCode = reasonCode.name();
        this.messageBn = messageBn;
        this.createdAt = createdAt;
    }

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

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getMessageBn() {
        return messageBn;
    }

    public void setMessageBn(String messageBn) {
        this.messageBn = messageBn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

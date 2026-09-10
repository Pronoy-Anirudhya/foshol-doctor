package com.rootcause.foshol.review.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advisory")
public class AdvisoryEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "disease_id")
    private UUID diseaseId;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @Column(nullable = false, length = 12)
    private String action;

    @Column(name = "officer_note_bn")
    private String officerNoteBn;

    @Column(nullable = false)
    private short version;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 60)
    private String updatedBy;

    protected AdvisoryEntity() {}

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

    public UUID getDiseaseId() {
        return diseaseId;
    }

    public void setDiseaseId(UUID diseaseId) {
        this.diseaseId = diseaseId;
    }

    public UUID getOfficerId() {
        return officerId;
    }

    public void setOfficerId(UUID officerId) {
        this.officerId = officerId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOfficerNoteBn() {
        return officerNoteBn;
    }

    public void setOfficerNoteBn(String officerNoteBn) {
        this.officerNoteBn = officerNoteBn;
    }

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public UUID getSupersedesId() {
        return supersedesId;
    }

    public void setSupersedesId(UUID supersedesId) {
        this.supersedesId = supersedesId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
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

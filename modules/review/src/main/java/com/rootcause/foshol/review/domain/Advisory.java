package com.rootcause.foshol.review.domain;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.util.BanglaNormalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Advisory {

    private final UUID id;
    private final UUID caseId;
    private final UUID diseaseId;
    private final UUID officerId;
    private final AdvisoryAction action;
    private final String officerNoteBn;
    private final short version;
    private final UUID supersedesId;
    private final List<AdvisoryRemedy> remedies;
    private final Instant publishedAt;
    private final Instant createdAt;
    private final String createdBy;

    public Advisory(
            UUID id,
            UUID caseId,
            UUID diseaseId,
            UUID officerId,
            AdvisoryAction action,
            String officerNoteBn,
            short version,
            UUID supersedesId,
            List<AdvisoryRemedy> remedies,
            Instant publishedAt,
            Instant createdAt,
            String createdBy) {
        if (diseaseId == null) {
            throw new IllegalArgumentException("diseaseId is required.");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1.");
        }
        if ((version == 1) != (supersedesId == null)) {
            throw new IllegalArgumentException("version = 1 if and only if supersedesId is null.");
        }
        this.id = id;
        this.caseId = caseId;
        this.diseaseId = diseaseId;
        this.officerId = officerId;
        this.action = action;
        this.officerNoteBn = officerNoteBn == null || officerNoteBn.isBlank()
                ? null
                : BanglaNormalizer.forStorage(officerNoteBn);
        this.version = version;
        this.supersedesId = supersedesId;
        this.remedies = List.copyOf(remedies);
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public static Advisory firstVersion(
            UUID id,
            UUID caseId,
            UUID diseaseId,
            UUID officerId,
            AdvisoryAction action,
            String officerNoteBn,
            List<AdvisoryRemedy> remedies,
            Instant publishedAt) {
        return new Advisory(
                id,
                caseId,
                diseaseId,
                officerId,
                action,
                officerNoteBn,
                (short) 1,
                null,
                remedies,
                publishedAt,
                publishedAt,
                officerId.toString());
    }

    public Advisory revise(
            UUID newId,
            UUID diseaseId,
            UUID officerId,
            AdvisoryAction action,
            String officerNoteBn,
            List<AdvisoryRemedy> remedies,
            Instant publishedAt) {
        return new Advisory(
                newId,
                caseId,
                diseaseId,
                officerId,
                action,
                officerNoteBn,
                (short) (version + 1),
                id,
                remedies,
                publishedAt,
                publishedAt,
                officerId.toString());
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public UUID diseaseId() {
        return diseaseId;
    }

    public UUID officerId() {
        return officerId;
    }

    public AdvisoryAction action() {
        return action;
    }

    public String officerNoteBn() {
        return officerNoteBn;
    }

    public short version() {
        return version;
    }

    public UUID supersedesId() {
        return supersedesId;
    }

    public List<AdvisoryRemedy> remedies() {
        return remedies;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }
}

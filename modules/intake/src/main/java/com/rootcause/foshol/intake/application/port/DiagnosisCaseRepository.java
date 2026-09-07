package com.rootcause.foshol.intake.application.port;

import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosisCaseRepository {

    void save(DiagnosisCase diagnosisCase);

    Optional<DiagnosisCase> findById(CaseId id);

    long countByFarmerSince(UUID farmerId, Instant since);

    Optional<IdempotencyRecord> findIdempotency(UUID key);

    void saveIdempotency(IdempotencyRecord record);

    void deleteExpiredIdempotency(Instant now);

    Optional<Instant> oldestCreatedAtAfter(UUID farmerId, Instant createdAfter);

    void upsertFarmerHistory(DiagnosisCase diagnosisCase, String cropNameBn, String thumbnailKey);

    void enrichAdvisory(
            UUID caseId,
            UUID advisoryId,
            int advisoryVersion,
            String diseaseNameBn,
            String officerName,
            Instant publishedAt);

    void enrichRejection(UUID caseId, String rejectionMessageBn);
}

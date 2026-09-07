package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.application.port.DuplicateIdempotencyKeyException;
import com.rootcause.foshol.intake.application.port.IdempotencyRepository;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DiagnosisCasePersistenceAdapter implements DiagnosisCaseRepository, IdempotencyRepository {

    private final DiagnosisCaseJpaRepository cases;
    private final IdempotencyKeyJpaRepository keys;
    private final JdbcClient jdbc;

    public DiagnosisCasePersistenceAdapter(
            DiagnosisCaseJpaRepository cases, IdempotencyKeyJpaRepository keys, JdbcClient jdbc) {
        this.cases = cases;
        this.keys = keys;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(DiagnosisCase diagnosisCase) {
        Optional<DiagnosisCaseEntity> existing = cases.findById(diagnosisCase.id().value());
        if (existing.isEmpty()) {
            cases.save(DiagnosisCaseMapper.toNewEntity(diagnosisCase));
            return;
        }
        DiagnosisCaseEntity entity = existing.get();
        DiagnosisCaseMapper.copyInto(diagnosisCase, entity);
        cases.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiagnosisCase> findById(CaseId id) {
        return cases.findById(id.value()).map(DiagnosisCaseMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByFarmerSince(UUID farmerId, Instant since) {
        return cases.countByFarmerIdAndCreatedAtAfter(farmerId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> oldestCreatedAtAfter(UUID farmerId, Instant createdAfter) {
        return cases.findFirstByFarmerIdAndCreatedAtAfterOrderByCreatedAtAsc(farmerId, createdAfter)
                .map(DiagnosisCaseEntity::getCreatedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findIdempotency(UUID key) {
        return toRecord(keys.findById(key));
    }

    @Override
    @Transactional
    public void saveIdempotency(IdempotencyRecord record) {
        insert(record);
    }

    @Override
    @Transactional
    public void deleteExpiredIdempotency(Instant now) {
        deleteExpired(now);
    }

    @Override
    @Transactional
    public void deleteExpired(Instant now) {
        keys.deleteByExpiresAtBefore(now);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByKey(UUID key) {
        return findIdempotency(key);
    }

    @Override
    @Transactional
    public void insert(IdempotencyRecord record) {
        try {
            keys.save(new IdempotencyKeyEntity(
                    record.key(),
                    record.farmerId(),
                    record.endpoint(),
                    record.requestHash().hex(),
                    (short) record.responseStatus(),
                    record.responseBody(),
                    record.createdAt(),
                    record.expiresAt()));
            keys.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateIdempotencyKeyException(ex);
        }
    }

    @Override
    @Transactional
    public void upsertFarmerHistory(DiagnosisCase diagnosisCase, String cropNameBn, String thumbnailKey) {
        jdbc.sql(
                        """
                        insert into p_farmer_case_history (
                            case_id, farmer_id, crop_name_bn, status, decision_path,
                            thumbnail_object_key, submitted_at, updated_at)
                        values (:caseId, :farmerId, :cropName, :status, :path, :thumb, :submitted, :updated)
                        on conflict (case_id) do update set
                            status = excluded.status,
                            decision_path = coalesce(excluded.decision_path, p_farmer_case_history.decision_path),
                            thumbnail_object_key = coalesce(excluded.thumbnail_object_key, p_farmer_case_history.thumbnail_object_key),
                            updated_at = excluded.updated_at
                        """)
                .param("caseId", diagnosisCase.id().value())
                .param("farmerId", diagnosisCase.farmerId())
                .param("cropName", cropNameBn)
                .param("status", diagnosisCase.status().name())
                .param("path", diagnosisCase.decisionPath() == null ? null : diagnosisCase.decisionPath().name())
                .param("thumb", thumbnailKey)
                .param("submitted", timestamp(diagnosisCase.createdAt()))
                .param("updated", timestamp(diagnosisCase.updatedAt()))
                .update();
    }

    @Override
    @Transactional
    public void enrichAdvisory(
            UUID caseId,
            UUID advisoryId,
            int advisoryVersion,
            String diseaseNameBn,
            String officerName,
            Instant publishedAt) {
        jdbc.sql(
                        """
                        update p_farmer_case_history
                           set advisory_id = :advisoryId,
                               advisory_version = :version,
                               disease_name_bn = :disease,
                               officer_name = :officer,
                               published_at = :published,
                               updated_at = :published
                         where case_id = :caseId
                        """)
                .param("advisoryId", advisoryId)
                .param("version", advisoryVersion)
                .param("disease", diseaseNameBn)
                .param("officer", officerName)
                .param("published", timestamp(publishedAt))
                .param("caseId", caseId)
                .update();
    }

    @Override
    @Transactional
    public void enrichRejection(UUID caseId, String rejectionMessageBn) {
        jdbc.sql(
                        """
                        update p_farmer_case_history
                           set rejection_message_bn = :message,
                               updated_at = now()
                         where case_id = :caseId
                        """)
                .param("message", rejectionMessageBn)
                .param("caseId", caseId)
                .update();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Optional<IdempotencyRecord> toRecord(Optional<IdempotencyKeyEntity> row) {
        return row.map(entity -> new IdempotencyRecord(
                entity.getKey(),
                entity.getFarmerId(),
                entity.getEndpoint(),
                new Sha256(entity.getRequestHash()),
                entity.getResponseStatus(),
                entity.getResponseBody(),
                entity.getCreatedAt(),
                entity.getExpiresAt()));
    }
}

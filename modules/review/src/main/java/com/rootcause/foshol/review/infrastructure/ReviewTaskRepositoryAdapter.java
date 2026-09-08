package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewTaskRepositoryAdapter implements ReviewTaskRepository {

    private final ReviewTaskJpaRepository jpa;

    public ReviewTaskRepositoryAdapter(ReviewTaskJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ReviewTask> findById(UUID id) {
        return jpa.findById(id).map(ReviewTaskMapper::toDomain);
    }

    @Override
    public Optional<ReviewTask> findByCaseId(UUID caseId) {
        return jpa.findByCaseId(caseId).map(ReviewTaskMapper::toDomain);
    }

    @Override
    public ReviewTask save(ReviewTask task) {
        ReviewTaskEntity entity = jpa.findById(task.id()).orElseGet(ReviewTaskEntity::new);
        ReviewTaskMapper.copy(task, entity);
        if (jpa.existsById(task.id())) {
            entity.setVersion(task.version());
        }
        return ReviewTaskMapper.toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public List<ReviewTask> lockExpiredClaims(Instant cutoff) {
        return jpa.lockExpiredClaims(cutoff).stream().map(ReviewTaskMapper::toDomain).toList();
    }

    @Override
    public List<ReviewTask> lockOverdueAssignments(Instant now) {
        return jpa.lockOverdueAssignments(now).stream().map(ReviewTaskMapper::toDomain).toList();
    }

    @Override
    public List<ReviewTask> lockOverdueResolutions(Instant now) {
        return jpa.lockOverdueResolutions(now).stream().map(ReviewTaskMapper::toDomain).toList();
    }

    @Override
    public List<ReviewTask> lockResolutionWarnings(Instant warnCutoff, Instant now) {
        return jpa.lockResolutionWarnings(warnCutoff, now).stream().map(ReviewTaskMapper::toDomain).toList();
    }

    @Override
    public long count() {
        return jpa.count();
    }
}

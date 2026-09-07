package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.review.application.port.OfficerQueueProjection;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OfficerQueueProjectionAdapter implements OfficerQueueProjectionPort {

    private final JdbcTemplate jdbc;

    public OfficerQueueProjectionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(OfficerQueueProjection row, Instant now) {
        jdbc.update(
                """
                insert into p_officer_queue (
                    case_id, review_task_id, farmer_name, crop_code, crop_name_bn, district_code,
                    decision_path, top_disease_id, top_disease_name_bn, top_confidence, image_count,
                    has_audio, analysis_mode, state, officer_id, is_resubmission, submitted_at,
                    sla_due_at, updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                row.caseId(),
                row.reviewTaskId(),
                nullToEmpty(row.farmerName()),
                nullToEmpty(row.cropCode()),
                nullToEmpty(row.cropNameBn()),
                truncateDistrict(row.districtCode()),
                row.decisionPath() == null ? null : row.decisionPath().name(),
                row.topDiseaseId(),
                row.topDiseaseNameBn(),
                row.topConfidence(),
                row.imageCount(),
                row.hasAudio(),
                row.analysisMode() == null ? null : row.analysisMode().name(),
                row.state().name(),
                row.officerId(),
                row.resubmission(),
                Timestamp.from(row.submittedAt()),
                Timestamp.from(row.slaDueAt()),
                Timestamp.from(now));
    }

    @Override
    public void updateState(UUID caseId, ReviewState state, UUID officerId, Instant now) {
        jdbc.update(
                "update p_officer_queue set state = ?, officer_id = ?, updated_at = ? where case_id = ?",
                state.name(),
                officerId,
                Timestamp.from(now),
                caseId);
    }

    @Override
    public boolean existsByCaseId(UUID caseId) {
        Boolean found = jdbc.queryForObject(
                "select exists(select 1 from p_officer_queue where case_id = ?)", Boolean.class, caseId);
        return Boolean.TRUE.equals(found);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncateDistrict(String value) {
        String v = nullToEmpty(value);
        return v.length() <= 8 ? v : v.substring(0, 8);
    }
}

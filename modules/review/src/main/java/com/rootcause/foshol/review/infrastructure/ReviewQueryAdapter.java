package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.application.query.OfficerQueueRow;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewQueryAdapter implements ReviewQueryPort {

    private final JdbcTemplate jdbc;

    public ReviewQueryAdapter(@ReadOnlyDataSource DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public OfficerQueuePage findQueue(OfficerQueueQuery query) {
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        int page = Math.max(query.page(), 0);
        String state = query.state() == null || query.state().isBlank() ? "PENDING" : query.state();
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1=1 ");
        if (!"ALL".equals(state)) {
            where.append(" and q.state = ? ");
            args.add(state);
        }
        if (query.mine()) {
            where.append(" and q.officer_id = ? ");
            args.add(query.officerId());
        }
        Long total = jdbc.queryForObject(
                "select count(*) from p_officer_queue q" + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;
        int totalPages = size == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<OfficerQueueRow> content = jdbc.query(
                """
                select q.case_id, q.review_task_id, q.farmer_name, q.crop_code, q.crop_name_bn, q.district_code,
                       q.decision_path, q.top_disease_id, q.top_disease_name_bn, q.top_confidence, q.image_count,
                       q.has_audio, q.analysis_mode, q.state, q.officer_id, q.is_resubmission,
                       t.requeue_count, q.submitted_at, q.sla_due_at
                from p_officer_queue q
                join review_task t on t.id = q.review_task_id
                """
                        + where
                        + " order by q.state, q.top_confidence asc nulls first, q.submitted_at asc limit ? offset ?",
                this::mapRow,
                pageArgs.toArray());
        return new OfficerQueuePage(content, page, size, totalElements, totalPages);
    }

    @Override
    public Optional<QueueTaskRow> findQueueRow(UUID reviewTaskId) {
        List<QueueTaskRow> rows = jdbc.query(
                """
                select q.case_id, q.review_task_id, q.farmer_name, q.crop_code, q.crop_name_bn, q.district_code,
                       q.decision_path, q.top_disease_id, q.top_disease_name_bn, q.top_confidence, q.image_count,
                       q.has_audio, q.analysis_mode, q.state, q.officer_id, q.is_resubmission,
                       t.requeue_count, q.submitted_at, q.sla_due_at
                from p_officer_queue q
                join review_task t on t.id = q.review_task_id
                where q.review_task_id = ?
                """,
                (rs, i) -> toQueueTaskRow(rs),
                reviewTaskId);
        return rows.stream().findFirst();
    }

    @Override
    public AdminStatsView loadStats(Instant dayStartUtc, BigDecimal confidenceHigh, BigDecimal confidenceLow) {
        return jdbc.queryForObject(
                """
                with published as (
                    select distinct on (a.case_id) a.case_id, a.disease_id, a.action
                    from advisory a
                    order by a.case_id, a.version desc
                ),
                agreement_pop as (
                    select p.case_id,
                           (p.disease_id = cc.disease_id) as agrees
                    from published p
                    join case_candidate cc on cc.case_id = p.case_id and cc.source = 'MODEL' and cc.rank = 1
                )
                select
                    (select count(*) from diagnosis_case where created_at >= ?) as cases_today,
                    (select count(*) filter (where action = 'APPROVED')::numeric / nullif(count(*), 0)
                        from published) as approval_rate,
                    (select percentile_cont(0.5) within group (order by extract(epoch from (updated_at - created_at)) / 60.0)
                        from review_task where state in ('DONE','REJECTED')) as median_minutes,
                    (select avg(agrees::int) from agreement_pop) as agreement_rate,
                    (select count(*) from agreement_pop) as agreement_sample
                """,
                (rs, i) -> new AdminStatsView(
                        rs.getLong("cases_today"),
                        (BigDecimal) rs.getObject("approval_rate"),
                        (BigDecimal) rs.getObject("median_minutes"),
                        (BigDecimal) rs.getObject("agreement_rate"),
                        rs.getLong("agreement_sample"),
                        confidenceHigh,
                        confidenceLow),
                Timestamp.from(dayStartUtc));
    }

    private OfficerQueueRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        QueueTaskRow raw = toQueueTaskRow(rs);
        return new OfficerQueueRow(
                raw.caseId(),
                raw.reviewTaskId(),
                raw.farmerName(),
                raw.cropCode(),
                raw.cropNameBn(),
                raw.districtCode(),
                raw.decisionPath() == null ? null : DecisionPath.valueOf(raw.decisionPath()),
                raw.topDiseaseId(),
                raw.topDiseaseNameBn(),
                raw.topConfidence(),
                raw.imageCount(),
                raw.hasAudio(),
                raw.analysisMode() == null ? null : AiMode.valueOf(raw.analysisMode()),
                ReviewState.valueOf(raw.state()),
                raw.officerId(),
                raw.resubmission(),
                raw.requeueCount(),
                raw.submittedAt(),
                raw.slaDueAt());
    }

    private QueueTaskRow toQueueTaskRow(ResultSet rs) throws SQLException {
        return new QueueTaskRow(
                rs.getObject("case_id", UUID.class),
                rs.getObject("review_task_id", UUID.class),
                rs.getString("farmer_name"),
                rs.getString("crop_code"),
                rs.getString("crop_name_bn"),
                rs.getString("district_code"),
                rs.getString("decision_path"),
                rs.getObject("top_disease_id", UUID.class),
                rs.getString("top_disease_name_bn"),
                rs.getBigDecimal("top_confidence"),
                rs.getInt("image_count"),
                rs.getBoolean("has_audio"),
                rs.getString("analysis_mode"),
                rs.getString("state"),
                rs.getObject("officer_id", UUID.class),
                rs.getBoolean("is_resubmission"),
                rs.getShort("requeue_count"),
                rs.getTimestamp("submitted_at").toInstant(),
                rs.getTimestamp("sla_due_at").toInstant());
    }
}

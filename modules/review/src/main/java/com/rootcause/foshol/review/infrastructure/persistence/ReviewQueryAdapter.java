package com.rootcause.foshol.review.infrastructure.persistence;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.jdbc.ReadOnlyDataSource;
import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.common.util.CatalogueLocale;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.review.application.query.KpiWarningView;
import com.rootcause.foshol.review.application.query.AdminCaseListCriteria;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.application.query.OfficerQueueRow;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewQueryAdapter implements ReviewQueryPort {

    private final JdbcTemplate jdbc;

    public ReviewQueryAdapter(@Qualifier("reviewReadOnlyDataSource") @ReadOnlyDataSource DataSource dataSource) {
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
        if (query.districtCode() != null && !query.districtCode().isBlank()) {
            where.append(" and q.district_code = ? ");
            args.add(query.districtCode());
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
                       t.requeue_count, q.submitted_at, q.sla_due_at, q.assignment_due_at, q.resolution_due_at
                from p_officer_queue q
                join review_task t on t.id = q.review_task_id
                """
                        + where
                        + " order by q.submitted_at desc limit ? offset ?",
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
                       t.requeue_count, q.submitted_at, q.sla_due_at, q.assignment_due_at, q.resolution_due_at
                from p_officer_queue q
                join review_task t on t.id = q.review_task_id
                where q.review_task_id = ?
                """,
                (rs, i) -> toQueueTaskRow(rs),
                reviewTaskId);
        return rows.stream().findFirst();
    }

    @Override
    public AdminStatsView loadStats(
            Instant dayStartUtc,
            Instant monthStartUtc,
            Instant yearStartUtc,
            BigDecimal confidenceHigh,
            BigDecimal confidenceLow,
            String districtCode) {
        return jdbc.queryForObject(
                """
                with published as (
                    select distinct on (a.case_id) a.case_id, a.disease_id, a.action
                    from advisory a
                    join diagnosis_case c on c.id = a.case_id
                    where c.district_code = ?
                    order by a.case_id, a.version desc
                ),
                agreement_pop as (
                    select p.case_id,
                           (p.disease_id = cc.disease_id) as agrees
                    from published p
                    join case_candidate cc on cc.case_id = p.case_id and cc.source = 'MODEL' and cc.rank = 1
                )
                select
                    (select count(*) from diagnosis_case where created_at >= ? and district_code = ?) as cases_today,
                    (select count(*) from diagnosis_case where created_at >= ? and district_code = ?) as cases_month,
                    (select count(*) from diagnosis_case where created_at >= ? and district_code = ?) as cases_year,
                    (select count(*) from diagnosis_case where district_code = ?) as cases_lifetime,
                    (select count(*) filter (where action = 'APPROVED')::numeric / nullif(count(*), 0)
                        from published) as approval_rate,
                    (select percentile_cont(0.5) within group (order by extract(epoch from (rt.updated_at - rt.created_at)) / 60.0)
                        from review_task rt
                        join diagnosis_case c on c.id = rt.case_id
                        where rt.state in ('DONE','REJECTED') and c.district_code = ?) as median_minutes,
                    (select avg(agrees::int) from agreement_pop) as agreement_rate,
                    (select count(*) from agreement_pop) as agreement_sample,
                    (select count(*) filter (where rt.state = 'REJECTED')::numeric
                            / nullif(count(*) filter (where rt.state in ('DONE','REJECTED')), 0)
                        from review_task rt
                        join diagnosis_case c on c.id = rt.case_id
                        where c.district_code = ?) as rejection_rate
                """,
                (rs, i) -> new AdminStatsView(
                        rs.getLong("cases_today"),
                        rs.getLong("cases_month"),
                        rs.getLong("cases_year"),
                        rs.getLong("cases_lifetime"),
                        decimal(rs, "approval_rate"),
                        decimal(rs, "median_minutes"),
                        decimal(rs, "agreement_rate"),
                        rs.getLong("agreement_sample"),
                        decimal(rs, "rejection_rate"),
                        confidenceHigh,
                        confidenceLow),
                districtCode,
                Timestamp.from(dayStartUtc),
                districtCode,
                Timestamp.from(monthStartUtc),
                districtCode,
                Timestamp.from(yearStartUtc),
                districtCode,
                districtCode,
                districtCode,
                districtCode);
    }

    @Override
    public OfficerQueuePage findAdminCases(AdminCaseListCriteria criteria) {
        int size = criteria.size() <= 0 ? 20 : Math.min(criteria.size(), 100);
        int page = Math.max(criteria.page(), 0);
        String state = criteria.state() == null || criteria.state().isBlank() ? "ALL" : criteria.state();
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where q.district_code = ? ");
        args.add(criteria.districtCode());
        if (!"ALL".equals(state)) {
            where.append(" and q.state = ? ");
            args.add(state);
        }
        if (criteria.submittedSince() != null) {
            where.append(" and q.submitted_at >= ? ");
            args.add(Timestamp.from(criteria.submittedSince()));
        }
        if (criteria.kpi() != null) {
            where.append(
                    " and exists (select 1 from kpi_breach b where b.review_task_id = q.review_task_id and b.kind = ?) ");
            args.add(criteria.kpi().name());
        }
        if (criteria.officerId() != null) {
            where.append(" and q.officer_id = ? ");
            args.add(criteria.officerId());
        }
        if (criteria.cropCode() != null && !criteria.cropCode().isBlank()) {
            where.append(" and q.crop_code = ? ");
            args.add(criteria.cropCode());
        }
        if (criteria.decisionPath() != null) {
            where.append(" and q.decision_path = ? ");
            args.add(criteria.decisionPath().name());
        }
        if (criteria.resubmission() != null) {
            where.append(" and q.is_resubmission = ? ");
            args.add(criteria.resubmission());
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
                       t.requeue_count, q.submitted_at, q.sla_due_at, q.assignment_due_at, q.resolution_due_at
                from p_officer_queue q
                join review_task t on t.id = q.review_task_id
                """
                        + where
                        + " order by q.submitted_at desc limit ? offset ?",
                this::mapRow,
                pageArgs.toArray());
        return new OfficerQueuePage(content, page, size, totalElements, totalPages);
    }

    @Override
    public List<KpiWarningView> findOpenResolutionWarnings(UUID officerId, Instant now, Duration warnBefore) {
        Timestamp nowTs = Timestamp.from(now);
        Timestamp cutoff = Timestamp.from(now.plus(warnBefore));
        return jdbc.query(
                """
                select t.case_id, t.id as review_task_id, t.resolution_due_at
                from review_task t
                where t.officer_id = ?
                  and t.state = 'CLAIMED'
                  and t.resolution_due_at is not null
                  and t.resolution_due_at > ?
                  and (t.kpi_warn_emitted_at is not null or t.resolution_due_at <= ?)
                order by t.resolution_due_at asc
                """,
                (rs, i) -> new KpiWarningView(
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("review_task_id", UUID.class),
                        rs.getTimestamp("resolution_due_at").toInstant(),
                        rs.getTimestamp("resolution_due_at").toInstant().minus(warnBefore)),
                officerId,
                nowTs,
                cutoff);
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static Instant timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private OfficerQueueRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        QueueTaskRow raw = toQueueTaskRow(rs);
        return new OfficerQueueRow(
                raw.caseId(),
                raw.reviewTaskId(),
                raw.farmerName(),
                raw.cropCode(),
                raw.cropNameBn(),
                CatalogueLocale.enOrBn(null, raw.cropNameBn()),
                CatalogueLocale.enFallback((String) null),
                raw.districtCode(),
                raw.decisionPath() == null ? null : DecisionPath.valueOf(raw.decisionPath()),
                raw.topDiseaseId(),
                raw.topDiseaseNameBn(),
                CatalogueLocale.enOrBn(null, raw.topDiseaseNameBn()),
                CatalogueLocale.enFallback(null, raw.topDiseaseNameBn()),
                raw.topConfidence(),
                raw.imageCount(),
                raw.hasAudio(),
                raw.analysisMode() == null ? null : AiMode.valueOf(raw.analysisMode()),
                ReviewState.valueOf(raw.state()),
                raw.officerId(),
                raw.resubmission(),
                raw.requeueCount(),
                raw.submittedAt(),
                raw.slaDueAt(),
                raw.assignmentDueAt(),
                raw.resolutionDueAt());
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
                rs.getTimestamp("sla_due_at").toInstant(),
                timestamp(rs, "assignment_due_at"),
                timestamp(rs, "resolution_due_at"));
    }
}

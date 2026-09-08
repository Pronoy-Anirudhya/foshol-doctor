package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.KpiKind;
import com.rootcause.foshol.review.application.port.KpiBreachPort;
import com.rootcause.foshol.review.domain.KpiBreach;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KpiBreachAdapter implements KpiBreachPort {

    private final JdbcTemplate jdbc;

    public KpiBreachAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(KpiBreach breach) {
        int rows = jdbc.update(
                """
                insert into kpi_breach (
                    id, review_task_id, case_id, district_code, kind, officer_id,
                    window_started_at, due_at, breached_at)
                values (?,?,?,?,?,?,?,?,?)
                on conflict (review_task_id, kind, window_started_at) do nothing
                """,
                breach.id(),
                breach.reviewTaskId(),
                breach.caseId(),
                breach.districtCode(),
                breach.kind().name(),
                breach.officerId(),
                Timestamp.from(breach.windowStartedAt()),
                Timestamp.from(breach.dueAt()),
                Timestamp.from(breach.breachedAt()));
        return rows > 0;
    }

    @Override
    public List<KpiOfficerCount> countResolutionByOfficer(String districtCode) {
        return jdbc.query(
                """
                select officer_id, count(*) as n
                from kpi_breach
                where district_code = ? and kind = 'RESOLUTION' and officer_id is not null
                group by officer_id
                order by n desc
                """,
                (rs, i) -> new KpiOfficerCount(rs.getObject("officer_id", UUID.class), rs.getLong("n")),
                districtCode);
    }

    @Override
    public long countByKind(String districtCode, KpiKind kind) {
        Long n = jdbc.queryForObject(
                "select count(*) from kpi_breach where district_code = ? and kind = ?",
                Long.class,
                districtCode,
                kind.name());
        return n == null ? 0 : n;
    }

    @Override
    public List<KpiBreachRow> findBreaches(
            String districtCode, KpiKind kind, UUID officerId, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                """
                select b.id, b.review_task_id, b.case_id, b.district_code, b.kind, b.officer_id,
                       b.window_started_at, b.due_at, b.breached_at,
                       coalesce(q.farmer_name, '') as farmer_name,
                       coalesce(q.crop_code, '') as crop_code,
                       coalesce(q.crop_name_bn, '') as crop_name_bn
                from kpi_breach b
                left join p_officer_queue q on q.case_id = b.case_id
                where b.district_code = ?
                """);
        args.add(districtCode);
        if (kind != null) {
            sql.append(" and b.kind = ? ");
            args.add(kind.name());
        }
        if (officerId != null) {
            sql.append(" and b.officer_id = ? ");
            args.add(officerId);
        }
        sql.append(" order by b.breached_at desc limit ? offset ? ");
        args.add(size);
        args.add(page * size);
        return jdbc.query(
                sql.toString(),
                (rs, i) -> new KpiBreachRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("review_task_id", UUID.class),
                        rs.getObject("case_id", UUID.class),
                        rs.getString("district_code"),
                        KpiKind.valueOf(rs.getString("kind")),
                        rs.getObject("officer_id", UUID.class),
                        rs.getTimestamp("window_started_at").toInstant(),
                        rs.getTimestamp("due_at").toInstant(),
                        rs.getTimestamp("breached_at").toInstant(),
                        rs.getString("farmer_name"),
                        rs.getString("crop_code"),
                        rs.getString("crop_name_bn")),
                args.toArray());
    }

    @Override
    public long countBreaches(String districtCode, KpiKind kind, UUID officerId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select count(*) from kpi_breach where district_code = ? ");
        args.add(districtCode);
        if (kind != null) {
            sql.append(" and kind = ? ");
            args.add(kind.name());
        }
        if (officerId != null) {
            sql.append(" and officer_id = ? ");
            args.add(officerId);
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }
}

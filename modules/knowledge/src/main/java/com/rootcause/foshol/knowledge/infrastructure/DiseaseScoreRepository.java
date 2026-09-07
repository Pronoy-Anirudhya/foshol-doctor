package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.application.port.DiseaseScoringPort;
import com.rootcause.foshol.knowledge.domain.DiseaseScore;
import com.rootcause.foshol.knowledge.domain.SymptomMatch;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DiseaseScoreRepository implements DiseaseScoringPort {

    private final JdbcClient jdbc;

    public DiseaseScoreRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public List<DiseaseScore> score(UUID cropId, List<SymptomMatch> matches) {
        if (cropId == null || matches == null || matches.isEmpty()) {
            return List.of();
        }
        StringJoiner values = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        for (SymptomMatch match : matches) {
            values.add("(?::uuid, ?::numeric)");
            params.add(match.symptomId());
            params.add(match.score());
        }
        String sql =
                """
                WITH matched(symptom_id, score) AS (VALUES %s)
                SELECT d.id, d.code, d.name_bn,
                       SUM(ds.weight * COALESCE(m.score, 0)) / SUM(ds.weight) AS score
                FROM   disease d
                JOIN   disease_symptom ds ON ds.disease_id = d.id
                LEFT   JOIN matched m ON m.symptom_id = ds.symptom_id
                WHERE  d.crop_id = ?
                  AND  d.deleted_at IS NULL
                  AND  d.is_healthy = false
                GROUP  BY d.id, d.code, d.name_bn
                HAVING SUM(ds.weight * COALESCE(m.score, 0)) > 0
                ORDER  BY score DESC, d.code ASC
                """
                        .formatted(values);
        params.add(cropId);
        List<DiseaseScore> scored = new ArrayList<>();
        var spec = jdbc.sql(sql);
        for (Object param : params) {
            spec = spec.param(param);
        }
        List<ScoredRow> rows = spec.query(ScoredRow.class).list();
        int rank = 1;
        for (ScoredRow row : rows) {
            scored.add(new DiseaseScore(row.id(), row.code(), row.nameBn(), row.score(), rank++));
        }
        return List.copyOf(scored);
    }

    public record ScoredRow(UUID id, String code, String nameBn, BigDecimal score) {}
}

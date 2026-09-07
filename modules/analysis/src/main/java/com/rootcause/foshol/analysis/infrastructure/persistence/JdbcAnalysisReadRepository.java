package com.rootcause.foshol.analysis.infrastructure.persistence;

import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.analysis.application.query.AnalysisReadRepository;
import com.rootcause.foshol.analysis.application.query.ReadOnlyDataSource;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.CandidateSource;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.SymptomSource;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.events.SymptomView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnalysisReadRepository implements AnalysisReadRepository {

    private final JdbcTemplate jdbc;
    private final KnowledgeQueryApi knowledge;

    public JdbcAnalysisReadRepository(@ReadOnlyDataSource DataSource dataSource, KnowledgeQueryApi knowledge) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.knowledge = knowledge;
    }

    @Override
    public Optional<AnalysisView> findByCaseId(UUID caseId) {
        List<AnalysisView> rows = jdbc.query(
                """
                select id, case_id, mode, vision_model_id, vision_model_version, asr_model_id, embed_model_id,
                       top1_confidence, top2_confidence, margin, decision_path, latency_ms,
                       gradcam_object_key, unmapped_labels::text, raw_output::text, error_code
                  from analysis_run
                 where case_id = ?
                 order by created_at desc
                 limit 1
                """,
                (rs, i) -> mapView(rs),
                caseId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<String> findGradcamObjectKey(UUID caseId) {
        List<String> keys = jdbc.query(
                """
                select gradcam_object_key from analysis_run
                 where case_id = ? and gradcam_object_key is not null
                 order by created_at desc limit 1
                """,
                (rs, i) -> rs.getString(1),
                caseId);
        return keys.isEmpty() ? Optional.empty() : Optional.ofNullable(keys.get(0));
    }

    @Override
    public Optional<UUID> findPrimaryGradcamImageId(UUID caseId) {
        Optional<String> key = findGradcamObjectKey(caseId);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String value = key.get();
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        if (slash < 0 || dot < 0 || dot <= slash) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.substring(slash + 1, dot)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private AnalysisView mapView(ResultSet rs) throws SQLException {
        UUID caseId = rs.getObject("case_id", UUID.class);
        DecisionPath path = DecisionPath.valueOf(rs.getString("decision_path"));
        String source = path == DecisionPath.SECONDARY ? CandidateSource.MERGED.name() : CandidateSource.MODEL.name();
        List<CandidateView> candidates = loadCandidates(caseId, source);
        List<SymptomView> symptoms = loadSymptoms(caseId);
        String raw = rs.getString("raw_output");
        String transcript = extractJsonString(raw, "transcriptBn");
        BigDecimal asr = extractJsonDecimal(raw, "asrConfidence");
        return new AnalysisView(
                caseId,
                path,
                AiMode.valueOf(rs.getString("mode")),
                rs.getBigDecimal("top1_confidence"),
                rs.getBigDecimal("top2_confidence"),
                rs.getBigDecimal("margin"),
                candidates,
                symptoms,
                transcript,
                asr,
                rs.getString("gradcam_object_key"),
                parseJsonArray(rs.getString("unmapped_labels")),
                rs.getString("vision_model_id"),
                rs.getString("vision_model_version"),
                rs.getInt("latency_ms"),
                rs.getString("error_code"));
    }

    private List<CandidateView> loadCandidates(UUID caseId, String source) {
        return jdbc.query(
                """
                select disease_id, confidence, "rank", source
                  from case_candidate
                 where case_id = ? and source = ?
                 order by "rank"
                """,
                (rs, i) -> {
                    UUID diseaseId = rs.getObject("disease_id", UUID.class);
                    Optional<DiseaseView> disease = knowledge.findDiseaseById(diseaseId);
                    return new CandidateView(
                            diseaseId,
                            disease.map(DiseaseView::code).orElse(""),
                            disease.map(DiseaseView::nameBn).orElse(""),
                            rs.getBigDecimal("confidence"),
                            rs.getInt("rank"),
                            CandidateSource.valueOf(rs.getString("source")));
                },
                caseId,
                source);
    }

    private List<SymptomView> loadSymptoms(UUID caseId) {
        return jdbc.query(
                """
                select symptom_id, score, source, matcher
                  from case_symptom
                 where case_id = ?
                 order by source, score desc
                """,
                (rs, i) -> {
                    UUID symptomId = rs.getObject("symptom_id", UUID.class);
                    Optional<SymptomRefView> ref = knowledge.listSymptoms().stream()
                            .filter(s -> s.id().equals(symptomId))
                            .findFirst();
                    return new SymptomView(
                            symptomId,
                            ref.map(SymptomRefView::code).orElse(""),
                            ref.map(SymptomRefView::nameBn).orElse(""),
                            rs.getBigDecimal("score"),
                            SymptomSource.valueOf(rs.getString("source")),
                            rs.getString("matcher"));
                },
                caseId);
    }

    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length());
        }
        if (trimmed.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String value = part.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            out.add(value);
        }
        return List.copyOf(out);
    }

    private static String extractJsonString(String json, String field) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + field + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int start = idx + needle.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static BigDecimal extractJsonDecimal(String json, String field) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + field + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int start = idx + needle.length();
        int end = start;
        while (end < json.length() && "0123456789.+-".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        if (end == start) {
            return null;
        }
        return new BigDecimal(json.substring(start, end));
    }
}

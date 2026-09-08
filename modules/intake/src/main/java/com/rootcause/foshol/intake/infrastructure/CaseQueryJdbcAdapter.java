package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.query.CaseDetailView;
import com.rootcause.foshol.intake.application.query.FarmerCaseRow;
import com.rootcause.foshol.intake.application.query.PageResult;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CaseQueryJdbcAdapter implements CaseQueryPort {

    private final JdbcClient jdbc;
    private final KnowledgeQueryApi knowledge;

    public CaseQueryJdbcAdapter(JdbcClient jdbc, KnowledgeQueryApi knowledge) {
        this.jdbc = jdbc;
        this.knowledge = knowledge;
    }

    @Override
    public Optional<CaseSummary> findSummary(UUID caseId) {
        return findFarmerId(caseId).flatMap(farmerId -> findDetail(caseId).map(detail -> toSummary(detail, farmerId)));
    }

    @Override
    public Optional<CaseDetailView> findDetail(UUID caseId) {
        Optional<CaseRow> row = jdbc.sql(
                        """
                        select id, farmer_id, crop_id, parent_case_id, status, decision_path, note_bn, created_at,
                               field_area, field_area_unit, crop_quantity, crop_quantity_unit, metrics_source
                          from diagnosis_case where id = :id
                        """)
                .param("id", caseId)
                .query(this::caseRow)
                .optional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        CaseRow c = row.get();
        String cropName = knowledge.findCropById(c.cropId()).map(crop -> crop.nameBn()).orElse("");
        List<CaseDetailView.ImageRefView> images = jdbc.sql(
                        """
                        select id, position, is_primary, quality_score, width, height
                          from case_image where case_id = :id order by position
                        """)
                .param("id", caseId)
                .query((rs, n) -> new CaseDetailView.ImageRefView(
                        rs.getObject("id", UUID.class),
                        rs.getInt("position"),
                        rs.getBoolean("is_primary"),
                        rs.getBigDecimal("quality_score"),
                        (Integer) rs.getObject("width"),
                        (Integer) rs.getObject("height")))
                .list();
        CaseDetailView.AudioRefView audio = jdbc.sql(
                        """
                        select id, duration_ms, transcript_bn from case_audio where case_id = :id
                        """)
                .param("id", caseId)
                .query((rs, n) -> new CaseDetailView.AudioRefView(
                        rs.getObject("id", UUID.class), rs.getInt("duration_ms"), rs.getString("transcript_bn")))
                .optional()
                .orElse(null);
        return Optional.of(new CaseDetailView(
                c.id(),
                c.cropId(),
                cropName,
                c.status(),
                c.decisionPath(),
                c.noteBn(),
                c.parentCaseId(),
                images,
                audio,
                c.createdAt(),
                c.fieldArea(),
                c.fieldAreaUnit(),
                c.cropQuantity(),
                c.cropQuantityUnit(),
                c.metricsSource()));
    }

    @Override
    public PageResult<FarmerCaseRow> listHistory(UUID farmerId, int page, int size) {
        long historyCount = jdbc.sql("select count(*) from p_farmer_case_history where farmer_id = :farmerId")
                .param("farmerId", farmerId)
                .query(Long.class)
                .single();
        if (historyCount > 0) {
            List<FarmerCaseRow> content = jdbc.sql(
                            """
                            select h.case_id, h.crop_name_bn, cast(null as uuid) as crop_id, h.status, h.decision_path,
                                   h.disease_name_bn, h.officer_name, h.advisory_version, h.rejection_message_bn,
                                   i.id as thumbnail_image_id, h.submitted_at, h.published_at
                              from p_farmer_case_history h
                              left join case_image i on i.case_id = h.case_id and i.is_primary = true
                             where h.farmer_id = :farmerId
                             order by h.submitted_at desc
                             limit :limit offset :offset
                            """)
                    .param("farmerId", farmerId)
                    .param("limit", size)
                    .param("offset", page * size)
                    .query(this::historyRow)
                    .list();
            return PageResult.of(content, page, size, historyCount);
        }
        long total = jdbc.sql("select count(*) from diagnosis_case where farmer_id = :farmerId")
                .param("farmerId", farmerId)
                .query(Long.class)
                .single();
        List<FarmerCaseRow> content = jdbc.sql(
                        """
                        select c.id as case_id, cast(null as varchar) as crop_name_bn, c.crop_id, c.status, c.decision_path,
                               cast(null as varchar) as disease_name_bn,
                               cast(null as varchar) as officer_name,
                               cast(null as smallint) as advisory_version,
                               cast(null as text) as rejection_message_bn,
                               i.id as thumbnail_image_id, c.created_at as submitted_at,
                               cast(null as timestamptz) as published_at
                          from diagnosis_case c
                          left join case_image i on i.case_id = c.id and i.is_primary = true
                         where c.farmer_id = :farmerId
                         order by c.created_at desc
                         limit :limit offset :offset
                        """)
                .param("farmerId", farmerId)
                .param("limit", size)
                .param("offset", page * size)
                .query(this::historyRow)
                .list();
        return PageResult.of(content, page, size, total);
    }

    @Override
    public Optional<ImageLocator> findImage(UUID caseId, UUID imageId) {
        return jdbc.sql(
                        """
                        select c.id as case_id, c.farmer_id, i.object_key, i.derivative_object_key
                          from case_image i join diagnosis_case c on c.id = i.case_id
                         where i.case_id = :caseId and i.id = :imageId
                        """)
                .param("caseId", caseId)
                .param("imageId", imageId)
                .query((rs, n) -> new ImageLocator(
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("farmer_id", UUID.class),
                        rs.getString("object_key"),
                        rs.getString("derivative_object_key")))
                .optional();
    }

    @Override
    public Optional<AudioLocator> findAudio(UUID caseId) {
        return jdbc.sql(
                        """
                        select c.id as case_id, c.farmer_id, a.object_key
                          from case_audio a join diagnosis_case c on c.id = a.case_id
                         where a.case_id = :caseId
                        """)
                .param("caseId", caseId)
                .query((rs, n) -> new AudioLocator(
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("farmer_id", UUID.class),
                        rs.getString("object_key")))
                .optional();
    }

    @Override
    public Optional<CaseStatus> findStatus(UUID caseId) {
        return jdbc.sql("select status from diagnosis_case where id = :id")
                .param("id", caseId)
                .query(String.class)
                .optional()
                .map(CaseStatus::valueOf);
    }

    @Override
    public Optional<UUID> findFarmerId(UUID caseId) {
        return jdbc.sql("select farmer_id from diagnosis_case where id = :id")
                .param("id", caseId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public Optional<String> findDistrictCode(UUID caseId) {
        return jdbc.sql("select district_code from diagnosis_case where id = :id")
                .param("id", caseId)
                .query(String.class)
                .optional();
    }

    private FarmerCaseRow historyRow(ResultSet rs, int rowNum) throws SQLException {
        String cropName = rs.getString("crop_name_bn");
        if (cropName == null) {
            UUID cropId = rs.getObject("crop_id", UUID.class);
            cropName = cropId == null
                    ? ""
                    : knowledge.findCropById(cropId).map(CropView::nameBn).orElse("");
        }
        return new FarmerCaseRow(
                rs.getObject("case_id", UUID.class),
                cropName,
                CaseStatus.valueOf(rs.getString("status")),
                rs.getString("decision_path") == null ? null : DecisionPath.valueOf(rs.getString("decision_path")),
                rs.getString("disease_name_bn"),
                rs.getString("officer_name"),
                (Integer) (rs.getObject("advisory_version") instanceof Number n ? n.intValue() : null),
                rs.getString("rejection_message_bn"),
                rs.getObject("thumbnail_image_id", UUID.class),
                rs.getTimestamp("submitted_at").toInstant(),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant());
    }

    private CaseRow caseRow(ResultSet rs, int rowNum) throws SQLException {
        String path = rs.getString("decision_path");
        Object parent = rs.getObject("parent_case_id");
        String areaUnit = rs.getString("field_area_unit");
        String qtyUnit = rs.getString("crop_quantity_unit");
        String metrics = rs.getString("metrics_source");
        return new CaseRow(
                rs.getObject("id", UUID.class),
                rs.getObject("farmer_id", UUID.class),
                rs.getObject("crop_id", UUID.class),
                parent == null ? null : (UUID) parent,
                CaseStatus.valueOf(rs.getString("status")),
                path == null ? null : DecisionPath.valueOf(path),
                rs.getString("note_bn"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBigDecimal("field_area"),
                areaUnit == null ? null : FieldAreaUnit.valueOf(areaUnit),
                rs.getBigDecimal("crop_quantity"),
                qtyUnit == null ? null : CropQuantityUnit.valueOf(qtyUnit),
                metrics == null ? null : MetricsSource.valueOf(metrics));
    }

    private CaseSummary toSummary(CaseDetailView detail, UUID farmerId) {
        String cropCode = knowledge.findCropById(detail.cropId()).map(c -> c.code()).orElse("");
        String district = jdbc.sql("select district_code from diagnosis_case where id = :id")
                .param("id", detail.caseId())
                .query(String.class)
                .optional()
                .orElse("");
        String division = jdbc.sql("select division_code from diagnosis_case where id = :id")
                .param("id", detail.caseId())
                .query(String.class)
                .optional()
                .orElse("");
        List<CaseImageRef> images = jdbc.sql(
                        """
                        select id, object_key, derivative_object_key, sha256, quality_score, is_primary, position
                          from case_image where case_id = :id order by position
                        """)
                .param("id", detail.caseId())
                .query((rs, n) -> new CaseImageRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("object_key"),
                        rs.getString("derivative_object_key"),
                        rs.getString("sha256"),
                        rs.getBigDecimal("quality_score"),
                        rs.getBoolean("is_primary"),
                        rs.getInt("position")))
                .list();
        CaseAudioRef audio = jdbc.sql(
                        """
                        select id, object_key, duration_ms, transcript_bn from case_audio where case_id = :id
                        """)
                .param("id", detail.caseId())
                .query((rs, n) -> new CaseAudioRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("object_key"),
                        rs.getInt("duration_ms"),
                        rs.getString("transcript_bn")))
                .optional()
                .orElse(null);
        String correlation = jdbc.sql("select correlation_id from diagnosis_case where id = :id")
                .param("id", detail.caseId())
                .query(String.class)
                .optional()
                .orElse("");
        return new CaseSummary(
                detail.caseId(),
                farmerId,
                detail.cropId(),
                cropCode,
                district,
                division,
                detail.status(),
                detail.decisionPath(),
                detail.noteBn(),
                detail.parentCaseId(),
                images,
                audio,
                correlation,
                detail.submittedAt(),
                detail.fieldArea(),
                detail.fieldAreaUnit(),
                detail.cropQuantity(),
                detail.cropQuantityUnit(),
                detail.metricsSource());
    }

    private record CaseRow(
            UUID id,
            UUID farmerId,
            UUID cropId,
            UUID parentCaseId,
            CaseStatus status,
            DecisionPath decisionPath,
            String noteBn,
            java.time.Instant createdAt,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource metricsSource) {}
}

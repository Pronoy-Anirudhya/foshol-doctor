package com.rootcause.foshol;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class OperationalTableWipe {

    private OperationalTableWipe() {}

    static void wipeOperationalTables(JdbcTemplate jdbc) {
        deleteQuietly(jdbc, "event_publication");
        jdbc.update("delete from notification");
        jdbc.update("delete from p_officer_queue");
        jdbc.update("delete from p_farmer_case_history");
        jdbc.update("delete from advisory_remedy");
        jdbc.update("delete from advisory");
        jdbc.update("delete from case_rejection");
        jdbc.update("delete from review_task");
        jdbc.update("delete from case_candidate");
        jdbc.update("delete from case_symptom");
        jdbc.update("delete from analysis_run");
        jdbc.update("delete from case_audio");
        jdbc.update("delete from case_image");
        jdbc.update("delete from idempotency_key");
        jdbc.update("delete from diagnosis_case");
        jdbc.update("delete from otp_challenge");
        jdbc.update("delete from field_officer");
        jdbc.update("delete from farmer");
    }

    private static void deleteQuietly(JdbcTemplate jdbc, String table) {
        try {
            jdbc.update("delete from " + table);
        } catch (DataAccessException ignored) {
            // Modulith event registry is created at context start; ignore if the table is absent.
        }
    }
}

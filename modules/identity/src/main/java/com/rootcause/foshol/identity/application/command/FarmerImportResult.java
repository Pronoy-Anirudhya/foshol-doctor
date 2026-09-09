package com.rootcause.foshol.identity.application.command;

import java.util.List;
import java.util.UUID;

public record FarmerImportResult(int succeeded, int failed, List<FarmerImportRowResult> results) {

    public record FarmerImportRowResult(
            int row, String status, UUID farmerId, String name, String errorCode, String message) {}
}

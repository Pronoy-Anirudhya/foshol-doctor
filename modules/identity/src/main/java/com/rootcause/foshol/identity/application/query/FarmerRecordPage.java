package com.rootcause.foshol.identity.application.query;

import java.util.List;

public record FarmerRecordPage(List<FarmerRecord> content, int page, int size, long totalElements, int totalPages) {}

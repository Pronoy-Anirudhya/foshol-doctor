package com.rootcause.foshol.identity.application.port;

import java.util.List;

public record FarmerDirectoryPage(
        List<FarmerDirectorySnapshot> content, int page, int size, long totalElements, int totalPages) {}

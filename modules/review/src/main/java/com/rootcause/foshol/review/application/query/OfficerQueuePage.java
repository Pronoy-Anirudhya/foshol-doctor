package com.rootcause.foshol.review.application.query;

import java.util.List;

public record OfficerQueuePage(List<OfficerQueueRow> content, int page, int size, long totalElements, int totalPages) {
    public OfficerQueuePage(List<OfficerQueueRow> content, int page, int size, long totalElements) {
        this(content, page, size, totalElements, size == 0 ? 0 : (int) Math.ceil(totalElements / (double) size));
    }
}

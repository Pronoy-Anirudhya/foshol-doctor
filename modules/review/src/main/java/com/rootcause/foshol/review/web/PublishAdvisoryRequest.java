package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.AdvisoryAction;
import java.util.List;
import java.util.UUID;

public record PublishAdvisoryRequest(
        AdvisoryAction action,
        UUID diseaseId,
        List<UUID> remedyIds,
        String officerNoteBn,
        Integer expectedVersion) {}

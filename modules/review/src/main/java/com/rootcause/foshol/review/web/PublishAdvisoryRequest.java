package com.rootcause.foshol.review.web;

import java.util.List;
import java.util.UUID;

public record PublishAdvisoryRequest(UUID diseaseId, List<UUID> remedyIds, String officerNoteBn) {}

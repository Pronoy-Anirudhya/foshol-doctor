package com.rootcause.foshol.review.web;

import java.util.List;
import java.util.UUID;

public record RecordSymptomsRequest(List<UUID> symptomIds) {}

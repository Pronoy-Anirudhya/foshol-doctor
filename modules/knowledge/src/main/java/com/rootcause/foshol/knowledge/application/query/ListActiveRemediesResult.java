package com.rootcause.foshol.knowledge.application.query;

import java.util.List;

public record ListActiveRemediesResult(boolean diseaseFound, List<RemedyReadModel> remedies) {}

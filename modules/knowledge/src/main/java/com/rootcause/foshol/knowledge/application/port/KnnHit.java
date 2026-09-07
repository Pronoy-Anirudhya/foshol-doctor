package com.rootcause.foshol.knowledge.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record KnnHit(UUID phraseId, UUID symptomId, BigDecimal similarity) {}

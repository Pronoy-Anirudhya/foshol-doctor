package com.rootcause.foshol.knowledge.application.port;

import java.util.UUID;

public interface CropIdIndex {

    boolean existsLive(UUID cropId);
}

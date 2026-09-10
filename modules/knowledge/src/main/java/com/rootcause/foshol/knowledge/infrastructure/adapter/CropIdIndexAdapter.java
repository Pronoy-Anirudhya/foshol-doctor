package com.rootcause.foshol.knowledge.infrastructure.adapter;

import com.rootcause.foshol.knowledge.application.port.CropIdIndex;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.rootcause.foshol.knowledge.infrastructure.persistence.CropJpaRepository;

@Component
public class CropIdIndexAdapter implements CropIdIndex {

    private final CropJpaRepository crops;

    public CropIdIndexAdapter(CropJpaRepository crops) {
        this.crops = crops;
    }

    @Override
    public boolean existsLive(UUID cropId) {
        if (cropId == null) {
            return false;
        }
        return crops.existsByIdAndDeletedAtIsNull(cropId);
    }
}

package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CropJpaRepository extends JpaRepository<CropEntity, UUID> {

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @Query(
            """
            select c.id as id, c.code as code, c.nameBn as nameBn, c.nameEn as nameEn,
                   c.iconKey as iconKey, c.displayOrder as displayOrder
            from CropEntity c
            where c.deletedAt is null
            order by c.displayOrder asc, c.code asc
            """)
    List<CropReadRow> findLiveCrops();

    @Query(
            """
            select c.id as id, c.code as code, c.nameBn as nameBn, c.nameEn as nameEn,
                   c.iconKey as iconKey, c.displayOrder as displayOrder
            from CropEntity c
            where c.id = :id and c.deletedAt is null
            """)
    Optional<CropReadRow> findLiveById(UUID id);

    @Query(
            """
            select c.id as id, c.code as code, c.nameBn as nameBn, c.nameEn as nameEn,
                   c.iconKey as iconKey, c.displayOrder as displayOrder
            from CropEntity c
            where c.code = :code and c.deletedAt is null
            """)
    Optional<CropReadRow> findLiveByCode(String code);
}

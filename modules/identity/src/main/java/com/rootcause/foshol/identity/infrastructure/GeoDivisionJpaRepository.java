package com.rootcause.foshol.identity.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoDivisionJpaRepository extends JpaRepository<GeoDivisionEntity, String> {

    List<GeoDivisionEntity> findAllByOrderByNameEnAsc();
}

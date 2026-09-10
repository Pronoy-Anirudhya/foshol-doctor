package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.OfficerReadPort;
import com.rootcause.foshol.identity.application.port.OfficerReadSnapshot;
import com.rootcause.foshol.identity.application.port.OfficerSnapshot;
import com.rootcause.foshol.identity.application.port.OfficerStore;
import com.rootcause.foshol.identity.infrastructure.persistence.FieldOfficerEntity;
import com.rootcause.foshol.identity.infrastructure.persistence.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.persistence.OfficerReadRow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOfficerStore implements OfficerStore, OfficerReadPort {

    private final FieldOfficerJpaRepository officers;

    public JpaOfficerStore(FieldOfficerJpaRepository officers) {
        this.officers = officers;
    }

    @Override
    public Optional<OfficerSnapshot> findById(UUID id) {
        return officers.findById(id).map(JpaOfficerStore::toSnapshot);
    }

    @Override
    public Optional<OfficerSnapshot> findByUsername(String username) {
        return officers.findByUsername(username).map(JpaOfficerStore::toSnapshot);
    }

    @Override
    public Optional<OfficerReadSnapshot> findReadById(UUID id) {
        return officers.findReadById(id).map(JpaOfficerStore::toRead);
    }

    private static OfficerSnapshot toSnapshot(FieldOfficerEntity entity) {
        return new OfficerSnapshot(
                entity.getId(),
                entity.getName(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.isActive(),
                entity.getRole(),
                entity.getDistrictCode(),
                entity.getDivisionCode());
    }

    private static OfficerReadSnapshot toRead(OfficerReadRow row) {
        return new OfficerReadSnapshot(
                row.getId(),
                row.getName(),
                row.getUsername(),
                row.getRole(),
                row.getDistrictCode(),
                row.getDivisionCode());
    }
}

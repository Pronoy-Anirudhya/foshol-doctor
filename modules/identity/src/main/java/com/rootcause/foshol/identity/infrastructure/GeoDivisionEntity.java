package com.rootcause.foshol.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "geo_division")
public class GeoDivisionEntity {

    @Id
    @Column(length = 8)
    private String code;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_bn", nullable = false)
    private String nameBn;

    protected GeoDivisionEntity() {}

    public String getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameBn() {
        return nameBn;
    }
}

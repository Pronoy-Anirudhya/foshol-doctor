package com.rootcause.foshol.review.application;

import com.rootcause.foshol.common.Role;
import java.util.UUID;

public record Actor(UUID id, Role role) {

    public boolean isOfficerOrAdmin() {
        return role == Role.OFFICER || role == Role.ADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isFarmer() {
        return role == Role.FARMER;
    }
}

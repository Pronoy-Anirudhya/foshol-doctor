package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.UUID;

public record RegisterFarmerCommand(
        UUID officerId,
        String name,
        String phone,
        String divisionCode,
        String districtCode,
        String preferredLanguage,
        String source,
        String idempotencyKey)
        implements Command {}

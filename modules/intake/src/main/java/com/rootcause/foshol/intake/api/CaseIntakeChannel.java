package com.rootcause.foshol.intake.api;

import java.util.UUID;

public interface CaseIntakeChannel {

    UUID submit(IntakeRequest request);
}

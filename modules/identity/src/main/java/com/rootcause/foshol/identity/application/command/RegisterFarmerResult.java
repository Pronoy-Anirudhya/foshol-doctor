package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.identity.application.query.FarmerRecord;

public record RegisterFarmerResult(FarmerRecord farmer, boolean replayed) {}

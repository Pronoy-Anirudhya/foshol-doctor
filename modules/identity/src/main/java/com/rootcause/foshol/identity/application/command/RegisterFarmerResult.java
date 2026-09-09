package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.identity.application.FarmerRecord;

public record RegisterFarmerResult(FarmerRecord farmer, boolean replayed) {}

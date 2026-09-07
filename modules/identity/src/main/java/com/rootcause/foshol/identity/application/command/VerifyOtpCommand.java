package com.rootcause.foshol.identity.application.command;

public record VerifyOtpCommand(String phone, String code) {}

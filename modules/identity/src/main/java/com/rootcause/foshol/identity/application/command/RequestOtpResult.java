package com.rootcause.foshol.identity.application.command;

public record RequestOtpResult(int expiresInSeconds, String otpDeliveryMode) {}

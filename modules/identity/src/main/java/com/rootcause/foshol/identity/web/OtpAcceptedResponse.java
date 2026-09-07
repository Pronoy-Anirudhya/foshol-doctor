package com.rootcause.foshol.identity.web;

public record OtpAcceptedResponse(int expiresInSeconds, String otpDeliveryMode) {}

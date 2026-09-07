package com.rootcause.foshol.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank String phone, @NotBlank @Pattern(regexp = "\\d+") String code) {}

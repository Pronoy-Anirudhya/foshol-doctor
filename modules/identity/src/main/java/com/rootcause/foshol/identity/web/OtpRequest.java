package com.rootcause.foshol.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequest(@NotBlank String phone) {}

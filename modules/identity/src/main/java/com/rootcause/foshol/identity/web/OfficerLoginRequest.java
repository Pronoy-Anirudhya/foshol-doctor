package com.rootcause.foshol.identity.web;

import jakarta.validation.constraints.NotBlank;

public record OfficerLoginRequest(@NotBlank String username, @NotBlank String password) {}

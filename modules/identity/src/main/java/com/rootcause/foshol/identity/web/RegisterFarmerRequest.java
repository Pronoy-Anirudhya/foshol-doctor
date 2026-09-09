package com.rootcause.foshol.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterFarmerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String phone,
        @NotBlank String divisionCode,
        @NotBlank String districtCode,
        @NotBlank @Pattern(regexp = "bn|en") String preferredLanguage) {}

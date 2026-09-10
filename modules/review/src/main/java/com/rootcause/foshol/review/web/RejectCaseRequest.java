package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.enums.RejectionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectCaseRequest(
        @NotNull RejectionReason reasonCode,
        @NotBlank @Size(max = 500) String messageBn,
        Integer expectedVersion) {}

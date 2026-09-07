package com.rootcause.foshol.intake.domain;

/**
 * Local quality-gate failure modes. Blocker: this enum belongs in {@code common}
 * per COMMON-ARCH-010 / INTAKE §3.3, but A1 has not shipped it yet.
 */
public enum QualityReason {
    BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    TOO_SMALL,
    UNREADABLE;

    public String httpReason() {
        return switch (this) {
            case TOO_DARK -> "UNDEREXPOSED";
            case TOO_BRIGHT -> "OVEREXPOSED";
            default -> name();
        };
    }

    public String messageKey() {
        return switch (this) {
            case BLURRY -> "intake.quality.blurry";
            case TOO_DARK -> "intake.quality.tooDark";
            case TOO_BRIGHT -> "intake.quality.tooBright";
            case TOO_SMALL -> "intake.quality.tooSmall";
            case UNREADABLE -> "intake.quality.unreadable";
        };
    }
}

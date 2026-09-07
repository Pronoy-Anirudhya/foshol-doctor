package com.rootcause.foshol.intake.domain.vo;

import com.rootcause.foshol.intake.domain.QualityReason;

public record QualityVerdict(boolean accepted, QualityReason reason) {

    public static QualityVerdict accept() {
        return new QualityVerdict(true, null);
    }

    public static QualityVerdict reject(QualityReason reason) {
        return new QualityVerdict(false, reason);
    }
}

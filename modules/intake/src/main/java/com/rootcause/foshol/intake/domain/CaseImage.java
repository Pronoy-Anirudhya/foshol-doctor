package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.ImageId;
import com.rootcause.foshol.intake.domain.vo.ImageQuality;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import com.rootcause.foshol.intake.domain.vo.Sha256;

public record CaseImage(
        ImageId id,
        ObjectKey objectKey,
        ObjectKey derivativeObjectKey,
        String contentType,
        int byteSize,
        ImageQuality quality,
        Sha256 sha256,
        boolean primary,
        int position) {

    public CaseImage asPrimary() {
        return new CaseImage(
                id, objectKey, derivativeObjectKey, contentType, byteSize, quality, sha256, true, position);
    }

    public CaseImage asSecondary() {
        return new CaseImage(
                id, objectKey, derivativeObjectKey, contentType, byteSize, quality, sha256, false, position);
    }
}

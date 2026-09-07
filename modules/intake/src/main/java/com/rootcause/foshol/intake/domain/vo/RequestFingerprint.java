package com.rootcause.foshol.intake.domain.vo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RequestFingerprint(Sha256 hash) {

    public RequestFingerprint {
        Objects.requireNonNull(hash, "hash");
    }

    public static RequestFingerprint compute(
            UUID farmerId,
            UUID cropId,
            String normalisedNote,
            UUID parentCaseId,
            List<Sha256> imageHashes,
            Sha256 audioHash) {
        Objects.requireNonNull(farmerId, "farmerId");
        Objects.requireNonNull(cropId, "cropId");
        Objects.requireNonNull(imageHashes, "imageHashes");
        String note = normalisedNote == null ? "" : normalisedNote;
        String parent = parentCaseId == null ? "" : parentCaseId.toString();
        StringBuilder payload = new StringBuilder();
        payload.append(farmerId).append('\n');
        payload.append(cropId).append('\n');
        payload.append(note).append('\n');
        payload.append(parent);
        for (Sha256 imageHash : imageHashes) {
            payload.append('\n').append(imageHash.hex());
        }
        payload.append('\n');
        if (audioHash != null) {
            payload.append(audioHash.hex());
        }
        return new RequestFingerprint(Sha256.ofUtf8(payload.toString()));
    }

    public String hex() {
        return hash.hex();
    }
}

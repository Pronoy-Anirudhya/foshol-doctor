package com.rootcause.foshol.intake.application.port;

import java.util.Optional;

public interface ImageTransformPort {

    /**
     * Returns a JPEG whose longest edge equals {@code maxEdgePx}, or empty when the
     * original is already at or below that size (caller then reuses the original key).
     */
    Optional<byte[]> jpegDerivative(byte[] original, int width, int height, int maxEdgePx);
}

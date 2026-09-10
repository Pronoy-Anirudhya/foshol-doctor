package com.rootcause.foshol.analysis.infrastructure.sidecar;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SidecarCallSupportRetryTest {

    @Test
    void retriesUnavailableAndBusyOnly() {
        assertThat(SidecarCallSupport.retryable(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "x")))
                .isTrue();
        assertThat(SidecarCallSupport.retryable(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_BUSY, "x")))
                .isTrue();
        assertThat(SidecarCallSupport.retryable(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNDECODABLE, "x")))
                .isFalse();
        assertThat(SidecarCallSupport.retryable(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_BAD_REQUEST, "x")))
                .isFalse();
        assertThat(SidecarCallSupport.retryable(
                        new SidecarFailureException(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT, "x")))
                .isFalse();
        assertThat(SidecarCallSupport.retryable(new IOException("reset"))).isTrue();
    }

    @Test
    void circuitIgnoresClientErrors() {
        assertThat(SidecarCallSupport.ignoreForCircuit(
                        new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNDECODABLE, "x")))
                .isTrue();
        assertThat(SidecarCallSupport.ignoreForCircuit(
                        new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "x")))
                .isFalse();
        assertThat(SidecarCallSupport.ignoreForCircuit(
                        new SidecarFailureException(ErrorCodes.ERR_SIDECAR_MODEL_UNAVAILABLE, "x")))
                .isTrue();
        assertThat(SidecarCallSupport.ignoreForCircuit(
                        new SidecarFailureException(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT, "asr")))
                .isTrue();
        assertThat(SidecarCallSupport.ignoreForCircuit(new java.util.concurrent.TimeoutException("deadline")))
                .isTrue();
    }
}

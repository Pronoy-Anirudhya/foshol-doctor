package com.rootcause.foshol.analysis.infrastructure.sidecar;

import com.rootcause.foshol.analysis.application.config.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SidecarCallSupport {

    public static final String INSTANCE = "sidecar";
    static final String ASR_ENDPOINT = "asr";
    private static final String METRIC = "foshol.ai.call";

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final TimeLimiter asrTimeLimiter;
    private final MeterRegistry meters;
    private final ExecutorService callExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SidecarCallSupport(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            TimeLimiterRegistry timeLimiters,
            MeterRegistry meters) {
        this(circuitBreakers, retries, timeLimiters, meters, timeLimiters.getDefaultConfig().getTimeoutDuration());
    }

    @Autowired
    public SidecarCallSupport(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            TimeLimiterRegistry timeLimiters,
            MeterRegistry meters,
            AnalysisSettings settings) {
        this(circuitBreakers, retries, timeLimiters, meters, settings.aiTimeout(), settings.deadline());
    }

    SidecarCallSupport(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            TimeLimiterRegistry timeLimiters,
            MeterRegistry meters,
            Duration timeout) {
        this(circuitBreakers, retries, timeLimiters, meters, timeout, timeout);
    }

    SidecarCallSupport(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            TimeLimiterRegistry timeLimiters,
            MeterRegistry meters,
            Duration timeout,
            Duration asrTimeout) {
        this.circuitBreaker = circuitBreakers.circuitBreaker(
                INSTANCE,
                () -> CircuitBreakerConfig.from(circuitBreakers.getDefaultConfig())
                        .ignoreException(SidecarCallSupport::ignoreForCircuit)
                        .build());
        this.retry = retries.retry(
                INSTANCE,
                () -> RetryConfig.from(retries.getDefaultConfig())
                        .retryOnException(SidecarCallSupport::retryable)
                        .build());
        this.timeLimiter = limiter(INSTANCE, timeout);
        this.asrTimeLimiter = timeout.equals(asrTimeout) ? this.timeLimiter : limiter(INSTANCE + "-asr", asrTimeout);
        this.meters = meters;
    }

    @PreDestroy
    void shutdown() {
        callExecutor.close();
    }

    public <T> T execute(String endpoint, Supplier<T> call) {
        Timer.Sample sample = Timer.start(meters);
        Supplier<T> decorated = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(circuitBreaker, wrapAsr(endpoint, call)));
        Callable<T> limited = TimeLimiter.decorateFutureSupplier(
                limiterFor(endpoint), () -> CompletableFuture.supplyAsync(decorated::get, callExecutor));
        try {
            T result = limited.call();
            sample.stop(timer(endpoint, "success"));
            return result;
        } catch (CallNotPermittedException ex) {
            sample.stop(timer(endpoint, "circuit_open"));
            throw new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar circuit is open", ex);
        } catch (SidecarFailureException ex) {
            sample.stop(timer(endpoint, "failure"));
            throw remapAsrTimeout(endpoint, ex);
        } catch (Exception ex) {
            sample.stop(timer(endpoint, "failure"));
            SidecarFailureException sidecar = unwrap(ex);
            if (sidecar != null) {
                throw remapAsrTimeout(endpoint, sidecar);
            }
            if (isAsr(endpoint) && SidecarTimeouts.isTimeout(ex)) {
                throw speechTimeout(ex);
            }
            throw new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar call failed", ex);
        }
    }

    static boolean retryable(Throwable ex) {
        SidecarFailureException sidecar = unwrap(ex);
        if (sidecar != null) {
            return sidecar.retryable();
        }
        return isTransport(ex);
    }

    static boolean ignoreForCircuit(Throwable ex) {
        SidecarFailureException sidecar = unwrap(ex);
        if (sidecar != null) {
            return sidecar.expectedExplainFailure() || sidecar.expectedSpeechDegradation();
        }
        return SidecarTimeouts.isTimeout(ex);
    }

    private static boolean isTransport(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static SidecarFailureException unwrap(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SidecarFailureException sidecar) {
                return sidecar;
            }
            current = current.getCause();
        }
        return null;
    }

    private TimeLimiter limiterFor(String endpoint) {
        return isAsr(endpoint) ? asrTimeLimiter : timeLimiter;
    }

    private static <T> Supplier<T> wrapAsr(String endpoint, Supplier<T> call) {
        if (!isAsr(endpoint)) {
            return call;
        }
        return () -> {
            try {
                return call.get();
            } catch (SidecarFailureException ex) {
                throw remapAsrTimeout(endpoint, ex);
            } catch (RuntimeException ex) {
                if (SidecarTimeouts.isTimeout(ex)) {
                    throw speechTimeout(ex);
                }
                throw ex;
            }
        };
    }

    private static SidecarFailureException remapAsrTimeout(String endpoint, SidecarFailureException ex) {
        if (!isAsr(endpoint) || ex.expectedSpeechDegradation()) {
            return ex;
        }
        if (SidecarTimeouts.isTimeout(ex)) {
            return speechTimeout(ex);
        }
        return ex;
    }

    private static SidecarFailureException speechTimeout(Throwable cause) {
        return new SidecarFailureException(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT, "Sidecar ASR timed out", cause);
    }

    private static boolean isAsr(String endpoint) {
        return ASR_ENDPOINT.equals(endpoint);
    }

    private static TimeLimiter limiter(String name, Duration timeout) {
        return TimeLimiter.of(
                name,
                TimeLimiterConfig.custom().timeoutDuration(timeout).cancelRunningFuture(true).build());
    }

    private Timer timer(String endpoint, String outcome) {
        return Timer.builder(METRIC).tag("endpoint", endpoint).tag("outcome", outcome).register(meters);
    }
}

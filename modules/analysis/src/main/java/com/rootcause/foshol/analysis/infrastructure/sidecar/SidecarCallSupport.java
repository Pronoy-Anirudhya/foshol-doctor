package com.rootcause.foshol.analysis.infrastructure.sidecar;

import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SidecarCallSupport {

    public static final String INSTANCE = "sidecar";
    private static final String METRIC = "foshol.ai.call";

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final MeterRegistry meters;

    public SidecarCallSupport(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            TimeLimiterRegistry timeLimiters,
            MeterRegistry meters) {
        this.circuitBreaker = circuitBreakers.circuitBreaker(INSTANCE);
        this.retry = retries.retry(INSTANCE);
        this.timeLimiter = timeLimiters.timeLimiter(INSTANCE);
        this.meters = meters;
    }

    public <T> T execute(String endpoint, Supplier<T> call) {
        Timer.Sample sample = Timer.start(meters);
        Supplier<T> decorated = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));
        Callable<T> limited = TimeLimiter.decorateFutureSupplier(
                timeLimiter, () -> CompletableFuture.supplyAsync(decorated::get));
        try {
            T result = limited.call();
            sample.stop(timer(endpoint, "success"));
            return result;
        } catch (CallNotPermittedException ex) {
            sample.stop(timer(endpoint, "circuit_open"));
            throw new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar circuit is open", ex);
        } catch (SidecarFailureException ex) {
            sample.stop(timer(endpoint, "failure"));
            throw ex;
        } catch (Exception ex) {
            sample.stop(timer(endpoint, "failure"));
            SidecarFailureException sidecar = unwrap(ex);
            if (sidecar != null) {
                throw sidecar;
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
        return sidecar != null && sidecar.clientError();
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

    private Timer timer(String endpoint, String outcome) {
        return Timer.builder(METRIC).tag("endpoint", endpoint).tag("outcome", outcome).register(meters);
    }
}

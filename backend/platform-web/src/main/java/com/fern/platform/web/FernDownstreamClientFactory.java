package com.fern.platform.web;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class FernDownstreamClientFactory {
    private final MeterRegistry meterRegistry;

    public FernDownstreamClientFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public RestClient createRestClient(FernDownstreamClientSpec spec) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(spec.properties().getConnectTimeout());
        requestFactory.setReadTimeout(spec.properties().getReadTimeout());
        return RestClient.builder()
                .baseUrl(spec.properties().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public CircuitBreaker createCircuitBreaker(FernDownstreamClientSpec spec) {
        FernDownstreamClientProperties.CircuitBreakerProperties properties = spec.properties().getCircuitBreaker();
        return CircuitBreaker.of(
                spec.callerService() + "-" + spec.targetService() + "-" + spec.operation(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(properties.getFailureRateThreshold())
                        .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                        .slidingWindowSize(properties.getSlidingWindowSize())
                        .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                        .build()
        );
    }

    public <T> T execute(
            FernDownstreamClientSpec spec,
            CircuitBreaker circuitBreaker,
            Supplier<T> supplier,
            FernDownstreamErrorMapper errorMapper
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T response = circuitBreaker == null ? supplier.get() : circuitBreaker.executeSupplier(supplier);
            sample.stop(timer(spec, "success", "200"));
            return response;
        } catch (CallNotPermittedException exception) {
            recordError(spec, "circuit_open", "503");
            sample.stop(timer(spec, "error", "503"));
            throw errorMapper.translateUnavailable(spec, exception);
        } catch (RestClientResponseException exception) {
            recordError(spec, "response_error", String.valueOf(exception.getStatusCode().value()));
            sample.stop(timer(spec, "error", String.valueOf(exception.getStatusCode().value())));
            throw errorMapper.translateResponse(spec, exception);
        } catch (RestClientException exception) {
            recordError(spec, "network_error", "503");
            sample.stop(timer(spec, "error", "503"));
            throw errorMapper.translateUnavailable(spec, exception);
        } catch (RuntimeException exception) {
            recordError(spec, "runtime_error", "500");
            sample.stop(timer(spec, "error", "500"));
            throw exception;
        }
    }

    private void recordError(FernDownstreamClientSpec spec, String outcome, String status) {
        meterRegistry.counter(
                "fern_downstream_client_error",
                "caller", spec.callerService(),
                "target", spec.targetService(),
                "operation", spec.operation(),
                "outcome", outcome,
                "status", status
        ).increment();
    }

    private Timer timer(FernDownstreamClientSpec spec, String outcome, String status) {
        return Timer.builder("fern_downstream_client_request")
                .tags(
                        "caller", spec.callerService(),
                        "target", spec.targetService(),
                        "operation", spec.operation(),
                        "outcome", outcome,
                        "status", status
                )
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}

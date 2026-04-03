package com.fern.financeservice.observability;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.web.UnhandledExceptionListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FinanceUnhandledExceptionListener implements UnhandledExceptionListener {
    private final Counter payrollRunFailureCounter;
    private final OperationalAlertPublisher operationalAlertPublisher;

    public FinanceUnhandledExceptionListener(MeterRegistry meterRegistry, OperationalAlertPublisher operationalAlertPublisher) {
        this.payrollRunFailureCounter = Counter.builder("fern_payroll_run_failures_total").register(meterRegistry);
        this.operationalAlertPublisher = operationalAlertPublisher;
    }

    @Override
    public void onUnhandled(HttpServletRequest request, Exception exception) {
        if (request.getRequestURI() == null || !request.getRequestURI().contains("/payroll-runs/")) {
            return;
        }
        String sanitizedError = ExceptionSummaries.safeSummary(exception);
        payrollRunFailureCounter.increment();
        operationalAlertPublisher.publish(
                "PAYROLL_RUN_FAILED",
                "HIGH",
                "Payroll run operation failed",
                request.getHeader(CorrelationId.HEADER),
                null,
                null,
                "HTTP_REQUEST",
                request.getRequestURI(),
                Map.of("errorMessage", sanitizedError, "path", request.getRequestURI())
        );
    }
}

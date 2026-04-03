package com.fern.procurementservice.observability;

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
public class ProcurementUnhandledExceptionListener implements UnhandledExceptionListener {
    private final Counter goodsReceiptPostFailureCounter;
    private final OperationalAlertPublisher operationalAlertPublisher;

    public ProcurementUnhandledExceptionListener(MeterRegistry meterRegistry, OperationalAlertPublisher operationalAlertPublisher) {
        this.goodsReceiptPostFailureCounter = Counter.builder("fern_goods_receipt_post_failures_total").register(meterRegistry);
        this.operationalAlertPublisher = operationalAlertPublisher;
    }

    @Override
    public void onUnhandled(HttpServletRequest request, Exception exception) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains("/goods-receipts/") || !uri.endsWith("/post")) {
            return;
        }
        String sanitizedError = ExceptionSummaries.safeSummary(exception);
        goodsReceiptPostFailureCounter.increment();
        operationalAlertPublisher.publish(
                "GOODS_RECEIPT_POST_FAILED",
                "HIGH",
                "Goods receipt posting failed",
                request.getHeader(CorrelationId.HEADER),
                null,
                null,
                "HTTP_REQUEST",
                uri,
                Map.of("errorMessage", sanitizedError, "path", uri)
        );
    }
}

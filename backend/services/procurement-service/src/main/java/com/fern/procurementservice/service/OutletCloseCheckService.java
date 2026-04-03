package com.fern.procurementservice.service;

import com.fern.procurementservice.dto.ProcurementResponses.OutletCloseCheckResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutletCloseCheckService {
    private final ProcurementJdbcRepository procurementJdbcRepository;

    public OutletCloseCheckService(ProcurementJdbcRepository procurementJdbcRepository) {
        this.procurementJdbcRepository = procurementJdbcRepository;
    }

    @Transactional(readOnly = true)
    public OutletCloseCheckResponse getOutletCloseCheck(Long outletId) {
        long blockingPurchaseOrders = procurementJdbcRepository.countBlockingPurchaseOrders(outletId);
        long blockingGoodsReceipts = procurementJdbcRepository.countBlockingGoodsReceipts(outletId);
        long blockingSupplierInvoices = procurementJdbcRepository.countBlockingSupplierInvoices(outletId);
        return new OutletCloseCheckResponse(
                outletId,
                blockingPurchaseOrders,
                blockingGoodsReceipts,
                blockingSupplierInvoices,
                blockingPurchaseOrders > 0 || blockingGoodsReceipts > 0 || blockingSupplierInvoices > 0
        );
    }
}

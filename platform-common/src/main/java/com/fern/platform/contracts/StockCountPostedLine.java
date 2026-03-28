package com.fern.platform.contracts;

import java.math.BigDecimal;

public record StockCountPostedLine(
        Long ingredientId,
        BigDecimal systemQty,
        BigDecimal actualQty,
        BigDecimal varianceQty,
        BigDecimal unitCost
) {
}

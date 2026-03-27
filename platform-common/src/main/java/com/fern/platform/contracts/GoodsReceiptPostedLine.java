package com.fern.platform.contracts;

import java.math.BigDecimal;

public record GoodsReceiptPostedLine(
        Long ingredientId,
        BigDecimal qtyReceived,
        BigDecimal unitCost,
        Long sourceLineId
) {
}

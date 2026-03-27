package com.fern.posservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.posservice.dto.PosCommands.OrderLineInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PosPricingService {
    private final PosCatalogClient catalogClient;

    public PosPricingService(PosCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    public PricingSnapshot resolvePricingSnapshot(
            FernPrincipal principal,
            Long outletId,
            LocalDate businessDate,
            List<OrderLineInput> requestedLines
    ) {
        MenuResponse menu = catalogClient.fetchMenu(principal, outletId, businessDate);
        Map<Long, MenuItem> menuItems = menu.items().stream().collect(Collectors.toMap(MenuItem::productId, item -> item));
        List<PricedLine> pricedLines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        int lineNumber = 1;
        for (OrderLineInput input : requestedLines) {
            MenuItem item = menuItems.get(input.productId());
            if (item == null) {
                throw new ConflictException("Product " + input.productId() + " is not active, available, or effectively priced for the outlet");
            }
            BigDecimal lineSubtotal = item.priceValue().multiply(input.qty()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxPercent = item.taxPercent() == null ? BigDecimal.ZERO : item.taxPercent();
            BigDecimal lineTax = lineSubtotal.multiply(taxPercent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax);
            pricedLines.add(new PricedLine(
                    lineNumber++,
                    item.productId(),
                    item.productCode(),
                    item.productName(),
                    item.priceValue(),
                    input.qty(),
                    BigDecimal.ZERO,
                    lineTax,
                    lineTotal,
                    input.note()
            ));
            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTax);
        }
        return new PricingSnapshot(pricedLines, subtotal, taxAmount, subtotal.add(taxAmount));
    }

    public List<RecipeSnapshot> resolveRecipeSnapshots(FernPrincipal principal, List<PricedLine> lines, LocalDate businessDate) {
        Set<Long> distinctIds = lines.stream()
                .map(PricedLine::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return catalogClient.resolveRecipes(principal, distinctIds.stream().toList(), businessDate);
    }

    public List<RecipeUsageItem> flattenUsage(List<PricedLine> lines, List<RecipeSnapshot> recipeSnapshots) {
        Map<Long, RecipeUsageItem> aggregated = new LinkedHashMap<>();
        Map<Long, RecipeSnapshot> snapshotByProductId = recipeSnapshots.stream()
                .collect(Collectors.toMap(RecipeSnapshot::productId, item -> item));
        for (PricedLine line : lines) {
            RecipeSnapshot recipeSnapshot = snapshotByProductId.get(line.productId());
            if (recipeSnapshot == null) {
                throw new ConflictException("Recipe snapshot not found for product " + line.productId());
            }
            for (RecipeIngredient ingredient : recipeSnapshot.ingredients()) {
                BigDecimal usageQty = ingredient.qty().multiply(line.qty()).setScale(4, RoundingMode.HALF_UP);
                aggregated.compute(ingredient.ingredientId(), (ingredientId, current) -> current == null
                        ? new RecipeUsageItem(
                                ingredient.ingredientId(),
                                ingredient.ingredientCode(),
                                ingredient.ingredientName(),
                                ingredient.uomCode(),
                                usageQty
                        )
                        : new RecipeUsageItem(
                                current.ingredientId(),
                                current.ingredientCode(),
                                current.ingredientName(),
                                current.uomCode(),
                                current.qty().add(usageQty).setScale(4, RoundingMode.HALF_UP)
                        ));
            }
        }
        return aggregated.values().stream().toList();
    }
}

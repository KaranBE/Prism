package com.airtribe.prism.common.exception;

import java.math.BigDecimal;

/** Thrown when a key's projected spend for the request would exceed its monthly cost budget. */
public class BudgetExhaustedException extends PrismException {
    public BudgetExhaustedException(String keyAlias, BigDecimal spent, BigDecimal budget) {
        super(ErrorCode.BUDGET_EXHAUSTED,
                "Virtual key '%s' has spent %s of its %s monthly budget".formatted(keyAlias, spent, budget));
    }
}

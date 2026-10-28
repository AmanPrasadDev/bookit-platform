package com.bookit.show.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Base ticket price per seat-section category (e.g. RECLINER, PRIME, CLASSIC) for one show.
 * Money is always BigDecimal, never double/float. Currency is INR in V1.
 */
public record ConfigureShowPricesRequest(
        @NotEmpty(message = "At least one category price is required")
        @Valid
        List<CategoryPrice> prices
) {
    public record CategoryPrice(
            @NotBlank(message = "Category code is required")
            String categoryCode,

            @NotNull(message = "Price amount is required")
            @DecimalMin(value = "0.00", message = "Price cannot be negative")
            BigDecimal amount
    ) {}
}

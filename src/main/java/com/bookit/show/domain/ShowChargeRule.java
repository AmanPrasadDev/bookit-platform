package com.bookit.show.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Defines tax or fee rules (e.g. convenience fee, GST) applicable to a pricing configuration.
 */
@Entity
@Table(name = "show_charge_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowChargeRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "price_configuration_id", nullable = false)
    private UUID priceConfigurationId;

    @Column(name = "charge_type", nullable = false, length = 50)
    private String chargeType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "rate_percentage", precision = 5, scale = 2)
    private BigDecimal ratePercentage;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

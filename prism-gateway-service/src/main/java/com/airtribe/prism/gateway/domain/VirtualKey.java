package com.airtribe.prism.gateway.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * A tenant's virtual API key: the unit of authentication, allowlisting, rate limiting
 * and budgeting in Prism. Provider credentials are never derived from or exposed
 * through this object - callers only ever see this alias-scoped key.
 */
@Entity
@Table(name = "virtual_keys", indexes = {
        // Every request looks a key up by its raw secret value first -> hottest read path
        // in the gateway, so it gets its own unique index rather than relying on the PK.
        @Index(name = "idx_virtual_keys_key_value", columnList = "key_value", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class VirtualKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_value", nullable = false, unique = true, length = 128)
    private String keyValue;

    @Column(name = "alias", nullable = false, length = 64)
    private String alias;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "virtual_key_allowed_models", joinColumns = @JoinColumn(name = "virtual_key_id"))
    @Column(name = "model_name")
    private Set<String> allowedModels;

    @Column(name = "requests_per_minute", nullable = false)
    private int requestsPerMinute;

    @Column(name = "monthly_budget_usd", nullable = false, precision = 12, scale = 4)
    private BigDecimal monthlyBudgetUsd;

    @Column(name = "monthly_spend_usd", nullable = false, precision = 12, scale = 4)
    private BigDecimal monthlySpendUsd = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Optimistic-lock version, kept as defence-in-depth alongside the atomic conditional-update
     *  query used for the hot budget-charge path (see VirtualKeyRepository#chargeIfWithinBudget). */
    @Version
    private long version;

    public boolean allowsModel(String model) {
        return allowedModels == null || allowedModels.isEmpty() || allowedModels.contains(model);
    }
}

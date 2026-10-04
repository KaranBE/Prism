package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.exception.ModelNotAllowedException;
import com.airtribe.prism.common.exception.UnknownModelAliasException;
import com.airtribe.prism.common.model.ModelTier;
import com.airtribe.prism.gateway.config.AliasDefinition;
import com.airtribe.prism.gateway.config.GatewayRuntimeConfig;
import com.airtribe.prism.gateway.config.PricingRegistry;
import com.airtribe.prism.gateway.domain.VirtualKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Strategy-pattern router: resolves the caller-supplied "model" field - a concrete model id,
 * a static alias ("fast"/"smart"), or the dynamic "auto" alias - into a concrete primary
 * model plus an ordered fallback chain, having already confirmed the virtual key is allowed
 * to use it.
 */
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final GatewayRuntimeConfig gatewayRuntimeConfig;
    private final PricingRegistry pricingRegistry;
    private final DifficultyClassifierService difficultyClassifierService;

    public RoutingPlan resolve(VirtualKey key, ChatCompletionRequest request) {
        String requested = request.model();

        if (!key.allowsModel(requested)) {
            throw new ModelNotAllowedException(key.getAlias(), requested);
        }

        if ("auto".equalsIgnoreCase(requested)) {
            ModelTier tier = difficultyClassifierService.classify(request);
            String resolvedAlias = tier == ModelTier.SMART ? "smart" : "fast";
            AliasDefinition def = aliasDef(resolvedAlias);
            return new RoutingPlan(requested, def.primary(), def.fallbackChain(), true);
        }

        AliasDefinition def = gatewayRuntimeConfig.aliases().get(requested.toLowerCase());
        if (def != null) {
            return new RoutingPlan(requested, def.primary(), def.fallbackChain(), false);
        }

        if (pricingRegistry.contains(requested)) {
            return new RoutingPlan(requested, requested, List.of(), false);
        }

        throw new UnknownModelAliasException(requested);
    }

    private AliasDefinition aliasDef(String alias) {
        AliasDefinition def = gatewayRuntimeConfig.aliases().get(alias);
        if (def == null) {
            throw new UnknownModelAliasException(alias);
        }
        return def;
    }
}

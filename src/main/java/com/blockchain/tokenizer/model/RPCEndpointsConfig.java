package com.blockchain.tokenizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration model for RPC endpoints loaded from external JSON file.
 * Defines public and private RPC endpoints with automatic fallback support.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RPCEndpointsConfig {

    @JsonProperty("publicEndpoints")
    private List<String> publicEndpoints;

    @JsonProperty("privateEndpoints")
    private List<String> privateEndpoints;

    /**
     * Returns all endpoints in priority order: private first, then public.
     *
     * @return ordered list of all available endpoints
     */
    public List<String> getAllEndpointsInPriorityOrder() {
        return List.of(
            privateEndpoints != null ? privateEndpoints : List.<String>of(),
            publicEndpoints != null ? publicEndpoints : List.<String>of()
        )
        .stream()
        .flatMap(List::stream)
        .toList();
    }

    /**
     * Validates that at least one endpoint is configured.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return (publicEndpoints != null && !publicEndpoints.isEmpty()) ||
               (privateEndpoints != null && !privateEndpoints.isEmpty());
    }
}

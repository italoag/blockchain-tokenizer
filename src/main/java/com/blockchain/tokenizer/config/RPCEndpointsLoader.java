package com.blockchain.tokenizer.config;

import com.blockchain.tokenizer.model.RPCEndpointsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Loads RPC endpoints configuration from external JSON file.
 * File path is configured via blockchain.rpc.config-path property.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RPCEndpointsLoader {

    private final BlockchainProperties blockchainProperties;
    private final ObjectMapper objectMapper;
    private RPCEndpointsConfig rpcEndpointsConfig;

    @PostConstruct
    public void loadConfiguration() {
        String configPath = blockchainProperties.getRpc().getConfigPath();

        try {
            File configFile = new File(configPath);

            if (!configFile.exists()) {
                log.error("RPC endpoints configuration file not found at: {}", configPath);
                throw new IllegalStateException("RPC endpoints configuration file not found: " + configPath);
            }

            rpcEndpointsConfig = objectMapper.readValue(configFile, RPCEndpointsConfig.class);

            if (!rpcEndpointsConfig.isValid()) {
                throw new IllegalStateException("RPC endpoints configuration is invalid: no endpoints configured");
            }

            log.info("Successfully loaded RPC endpoints configuration from: {}", configPath);
            log.info("Private endpoints: {}", rpcEndpointsConfig.getPrivateEndpoints());
            log.info("Public endpoints: {}", rpcEndpointsConfig.getPublicEndpoints());

        } catch (IOException e) {
            log.error("Failed to load RPC endpoints configuration from: {}", configPath, e);
            throw new IllegalStateException("Failed to load RPC endpoints configuration", e);
        }
    }

    /**
     * Returns the loaded RPC endpoints configuration.
     *
     * @return RPC endpoints configuration
     */
    public RPCEndpointsConfig getRpcEndpointsConfig() {
        if (rpcEndpointsConfig == null) {
            throw new IllegalStateException("RPC endpoints configuration not loaded");
        }
        return rpcEndpointsConfig;
    }
}

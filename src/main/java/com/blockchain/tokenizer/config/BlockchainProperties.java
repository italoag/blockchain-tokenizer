package com.blockchain.tokenizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for blockchain interactions.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    private RpcConfig rpc = new RpcConfig();
    private ChainConfig chain = new ChainConfig();
    private GasConfig gas = new GasConfig();
    private NonceConfig nonce = new NonceConfig();

    @Data
    public static class RpcConfig {
        private String configPath;
        private long requestTimeout = 10000L;
        private long connectionTimeout = 5000L;
        private int maxRetryAttempts = 3;
        private long retryDelayMs = 1000L;
    }

    @Data
    public static class ChainConfig {
        private long id = 1L;
        private String networkName = "mainnet";
    }

    @Data
    public static class GasConfig {
        private int priceBufferPercentage = 10;
        private int limitBufferPercentage = 20;
        private long maxPriorityFeePerGas = 2000000000L; // 2 Gwei
        private long maxFeePerGas = 100000000000L; // 100 Gwei
    }

    @Data
    public static class NonceConfig {
        private long cacheTtlSeconds = 300L;
        private long lockTimeoutSeconds = 10L;
    }
}

package com.blockchain.tokenizer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for Web3j and HTTP client.
 */
@Slf4j
@Configuration
public class Web3jConfig {

    /**
     * Creates a shared ObjectMapper for JSON serialization/deserialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Creates a configured OkHttpClient for Web3j HTTP connections.
     * This client is shared across all Web3j instances for efficiency.
     */
    @Bean
    public OkHttpClient okHttpClient(BlockchainProperties properties) {
        return new OkHttpClient.Builder()
            .connectTimeout(properties.getRpc().getConnectionTimeout(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getRpc().getRequestTimeout(), TimeUnit.MILLISECONDS)
            .writeTimeout(properties.getRpc().getRequestTimeout(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    /**
     * Creates a Web3j instance. Note: This is a placeholder.
     * The actual Web3j instances are created dynamically by RPCClient
     * for each endpoint with fallback support.
     */
    @Bean
    public Web3j web3j() {
        // This is just a placeholder bean to satisfy Spring context
        // The actual Web3j instances are managed by RPCClient
        log.info("Web3j configuration initialized - actual instances managed by RPCClient");
        return null;
    }
}

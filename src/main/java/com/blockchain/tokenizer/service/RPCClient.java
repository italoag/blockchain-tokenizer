package com.blockchain.tokenizer.service;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.config.RPCEndpointsLoader;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.http.HttpService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * RPC Client with automatic fallback between multiple endpoints.
 * Prioritizes private endpoints over public endpoints and handles failures gracefully.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RPCClient {

    private final RPCEndpointsLoader endpointsLoader;
    private final BlockchainProperties blockchainProperties;
    private final OkHttpClient okHttpClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final ConcurrentHashMap<String, Web3j> web3jInstances = new ConcurrentHashMap<>();
    private List<String> endpoints;
    private CircuitBreaker circuitBreaker;

    @PostConstruct
    public void init() {
        endpoints = endpointsLoader.getRpcEndpointsConfig().getAllEndpointsInPriorityOrder();
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("rpcClient");

        log.info("Initialized RPCClient with {} endpoints in priority order", endpoints.size());
        endpoints.forEach(endpoint -> log.debug("  - {}", endpoint));
    }

    /**
     * Executes a Web3j request with automatic fallback across all configured endpoints.
     *
     * @param requestFunction function that takes Web3j and returns a Request
     * @param <T> response type
     * @return Mono of response
     */
    public <T, R extends Response<T>> Mono<R> executeWithFallback(
        Function<Web3j, Request<?, R>> requestFunction
    ) {
        return Flux.fromIterable(endpoints)
            .concatMap(endpoint -> executeOnEndpoint(endpoint, requestFunction))
            .next()
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .doOnError(error -> log.error("All RPC endpoints failed", error))
            .onErrorResume(error -> Mono.error(
                new RuntimeException("All RPC endpoints failed after exhausting fallback options", error)
            ));
    }

    /**
     * Executes a request on a specific endpoint with retry logic.
     */
    private <T, R extends Response<T>> Mono<R> executeOnEndpoint(
        String endpoint,
        Function<Web3j, Request<?, R>> requestFunction
    ) {
        return Mono.fromCallable(() -> {
                Web3j web3j = getOrCreateWeb3j(endpoint);
                Request<?, R> request = requestFunction.apply(web3j);
                R response = request.send();

                if (response.hasError()) {
                    log.warn("RPC error on endpoint {}: {}", endpoint, response.getError().getMessage());
                    throw new RuntimeException("RPC error: " + response.getError().getMessage());
                }

                log.debug("Successfully executed request on endpoint: {}", endpoint);
                return response;
            })
            .retryWhen(Retry.backoff(
                    blockchainProperties.getRpc().getMaxRetryAttempts(),
                    Duration.ofMillis(blockchainProperties.getRpc().getRetryDelayMs())
                )
                .doBeforeRetry(signal -> log.warn(
                    "Retrying request on endpoint {} (attempt {})",
                    endpoint,
                    signal.totalRetries() + 1
                ))
            )
            .doOnError(error -> log.warn("Endpoint {} failed: {}", endpoint, error.getMessage()))
            .onErrorResume(error -> Mono.empty()); // Continue to next endpoint on error
    }

    /**
     * Gets or creates a Web3j instance for the given endpoint.
     */
    private Web3j getOrCreateWeb3j(String endpoint) {
        return web3jInstances.computeIfAbsent(endpoint, url -> {
            HttpService httpService = new HttpService(url, okHttpClient);
            log.debug("Created new Web3j instance for endpoint: {}", url);
            return Web3j.build(httpService);
        });
    }

    /**
     * Gets the primary (first priority) Web3j instance.
     */
    public Web3j getPrimaryWeb3j() {
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("No RPC endpoints configured");
        }
        return getOrCreateWeb3j(endpoints.get(0));
    }

    /**
     * Closes all Web3j connections.
     */
    public void shutdown() {
        web3jInstances.values().forEach(Web3j::shutdown);
        web3jInstances.clear();
        log.info("Shut down all Web3j connections");
    }
}

package com.blockchain.tokenizer.manager;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.service.RPCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterName;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.time.Duration;

/**
 * Manages transaction nonces with Redis-backed caching for consistency.
 * Ensures that each address gets a unique, sequential nonce even in concurrent scenarios.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NonceManager {

    private final RPCClient rpcClient;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final BlockchainProperties blockchainProperties;

    private static final String NONCE_KEY_PREFIX = "nonce:";
    private static final String NONCE_LOCK_PREFIX = "nonce:lock:";

    /**
     * Gets the next available nonce for the given address.
     * Uses Redis to cache and increment nonces atomically.
     *
     * @param address the Ethereum address
     * @return Mono of next nonce
     */
    public Mono<BigInteger> getNextNonce(String address) {
        String normalizedAddress = address.toLowerCase();
        String nonceKey = NONCE_KEY_PREFIX + normalizedAddress;
        String lockKey = NONCE_LOCK_PREFIX + normalizedAddress;

        return acquireLock(lockKey)
            .flatMap(lockAcquired -> {
                if (!lockAcquired) {
                    log.warn("Failed to acquire lock for address: {}", normalizedAddress);
                    return Mono.error(new RuntimeException("Could not acquire nonce lock for address: " + address));
                }

                return getCachedNonce(nonceKey)
                    .switchIfEmpty(fetchAndCacheNonceFromBlockchain(normalizedAddress, nonceKey))
                    .flatMap(nonce -> incrementAndCacheNonce(nonceKey, nonce))
                    .doFinally(signalType -> releaseLock(lockKey).subscribe());
            });
    }

    /**
     * Resets the cached nonce for an address (useful after transaction failures).
     */
    public Mono<Void> resetNonce(String address) {
        String normalizedAddress = address.toLowerCase();
        String nonceKey = NONCE_KEY_PREFIX + normalizedAddress;

        return redisTemplate.delete(nonceKey)
            .doOnSuccess(deleted -> log.info("Reset nonce cache for address: {}", normalizedAddress))
            .then();
    }

    /**
     * Acquires a distributed lock using Redis.
     */
    private Mono<Boolean> acquireLock(String lockKey) {
        Duration lockTimeout = Duration.ofSeconds(blockchainProperties.getNonce().getLockTimeoutSeconds());

        return redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", lockTimeout)
            .defaultIfEmpty(false)
            .doOnNext(acquired -> {
                if (acquired) {
                    log.debug("Acquired lock: {}", lockKey);
                }
            });
    }

    /**
     * Releases the distributed lock.
     */
    private Mono<Void> releaseLock(String lockKey) {
        return redisTemplate.delete(lockKey)
            .doOnSuccess(deleted -> log.debug("Released lock: {}", lockKey))
            .then();
    }

    /**
     * Gets the cached nonce from Redis.
     */
    private Mono<BigInteger> getCachedNonce(String nonceKey) {
        return redisTemplate.opsForValue()
            .get(nonceKey)
            .map(BigInteger::new)
            .doOnNext(nonce -> log.debug("Retrieved cached nonce: {} from key: {}", nonce, nonceKey));
    }

    /**
     * Fetches nonce from blockchain and caches it in Redis.
     */
    private Mono<BigInteger> fetchAndCacheNonceFromBlockchain(String address, String nonceKey) {
        return rpcClient.executeWithFallback(web3j ->
                web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
            )
            .map(ethGetTransactionCount -> ethGetTransactionCount.getTransactionCount())
            .flatMap(nonce -> cacheNonce(nonceKey, nonce).thenReturn(nonce))
            .doOnNext(nonce -> log.info("Fetched nonce {} from blockchain for address: {}", nonce, address));
    }

    /**
     * Increments the nonce and caches it.
     */
    private Mono<BigInteger> incrementAndCacheNonce(String nonceKey, BigInteger currentNonce) {
        BigInteger nextNonce = currentNonce.add(BigInteger.ONE);

        return cacheNonce(nonceKey, nextNonce)
            .thenReturn(currentNonce)  // Return current nonce (before increment)
            .doOnNext(nonce -> log.debug("Incremented nonce to {} for key: {}", nextNonce, nonceKey));
    }

    /**
     * Caches the nonce in Redis with TTL.
     */
    private Mono<Boolean> cacheNonce(String nonceKey, BigInteger nonce) {
        Duration ttl = Duration.ofSeconds(blockchainProperties.getNonce().getCacheTtlSeconds());

        return redisTemplate.opsForValue()
            .set(nonceKey, nonce.toString(), ttl)
            .defaultIfEmpty(false);
    }
}

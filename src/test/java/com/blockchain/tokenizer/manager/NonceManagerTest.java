package com.blockchain.tokenizer.manager;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.service.RPCClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NonceManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NonceManager Tests")
class NonceManagerTest {

    @Mock
    private RPCClient rpcClient;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private BlockchainProperties blockchainProperties;

    private NonceManager nonceManager;

    @BeforeEach
    void setUp() {
        blockchainProperties = new BlockchainProperties();
        blockchainProperties.setNonce(new BlockchainProperties.NonceConfig());
        blockchainProperties.getNonce().setCacheTtlSeconds(300L);
        blockchainProperties.getNonce().setLockTimeoutSeconds(10L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        nonceManager = new NonceManager(rpcClient, redisTemplate, blockchainProperties);
    }

    @Test
    @DisplayName("Should get next nonce from cache when available")
    void shouldGetNextNonceFromCache() {
        // Given
        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        BigInteger cachedNonce = BigInteger.valueOf(5);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(cachedNonce.toString()));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString()))
            .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(nonceManager.getNextNonce(address))
            .assertNext(nonce -> {
                assertThat(nonce).isEqualTo(cachedNonce);
            })
            .verifyComplete();

        verify(valueOperations).setIfAbsent(contains("lock"), eq("1"), any(Duration.class));
        verify(valueOperations).get(contains("nonce:"));
        verify(valueOperations).set(contains("nonce:"), eq("6"), any(Duration.class));
    }

    @Test
    @DisplayName("Should fetch nonce from blockchain when cache is empty")
    void shouldFetchNonceFromBlockchainWhenCacheEmpty() {
        // Given
        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        BigInteger blockchainNonce = BigInteger.valueOf(10);

        EthGetTransactionCount ethGetTransactionCount = mock(EthGetTransactionCount.class);
        when(ethGetTransactionCount.getTransactionCount()).thenReturn(blockchainNonce);
        when(ethGetTransactionCount.hasError()).thenReturn(false);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString()))
            .thenReturn(Mono.just(1L));
        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethGetTransactionCount));

        // When & Then
        StepVerifier.create(nonceManager.getNextNonce(address))
            .assertNext(nonce -> {
                assertThat(nonce).isEqualTo(blockchainNonce);
            })
            .verifyComplete();

        verify(rpcClient).executeWithFallback(any());
        verify(valueOperations).set(contains("nonce:"), eq("11"), any(Duration.class));
    }

    @Test
    @DisplayName("Should reset nonce cache successfully")
    void shouldResetNonceCache() {
        // Given
        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";

        when(redisTemplate.delete(anyString()))
            .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(nonceManager.resetNonce(address))
            .verifyComplete();

        verify(redisTemplate).delete(contains("nonce:"));
    }

    @Test
    @DisplayName("Should fail when lock cannot be acquired")
    void shouldFailWhenLockCannotBeAcquired() {
        // Given
        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(nonceManager.getNextNonce(address))
            .expectErrorMessage("Could not acquire nonce lock for address: " + address)
            .verify();

        verify(valueOperations).setIfAbsent(contains("lock"), eq("1"), any(Duration.class));
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("Should normalize address to lowercase")
    void shouldNormalizeAddressToLowercase() {
        // Given
        String address = "0x742D35CC6634C0532925A3B844BC9E7595F0BEB";
        BigInteger cachedNonce = BigInteger.valueOf(3);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(cachedNonce.toString()));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString()))
            .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(nonceManager.getNextNonce(address))
            .assertNext(nonce -> {
                assertThat(nonce).isEqualTo(cachedNonce);
            })
            .verifyComplete();

        verify(valueOperations).get(contains("0x742d35cc6634c0532925a3b844bc9e7595f0beb"));
    }

    @Test
    @DisplayName("Should increment nonce correctly")
    void shouldIncrementNonceCorrectly() {
        // Given
        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        BigInteger currentNonce = BigInteger.valueOf(7);
        BigInteger expectedNextNonce = BigInteger.valueOf(8);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just(currentNonce.toString()));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString()))
            .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(nonceManager.getNextNonce(address))
            .assertNext(nonce -> {
                assertThat(nonce).isEqualTo(currentNonce);
            })
            .verifyComplete();

        verify(valueOperations).set(contains("nonce:"), eq(expectedNextNonce.toString()), any(Duration.class));
    }
}

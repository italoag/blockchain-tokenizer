package com.blockchain.tokenizer.manager;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.service.RPCClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GasManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GasManager Tests")
class GasManagerTest {

    @Mock
    private RPCClient rpcClient;

    private BlockchainProperties blockchainProperties;

    private GasManager gasManager;

    @BeforeEach
    void setUp() {
        blockchainProperties = new BlockchainProperties();
        blockchainProperties.setGas(new BlockchainProperties.GasConfig());
        blockchainProperties.getGas().setPriceBufferPercentage(10);
        blockchainProperties.getGas().setLimitBufferPercentage(20);
        blockchainProperties.getGas().setMaxPriorityFeePerGas(2000000000L);
        blockchainProperties.getGas().setMaxFeePerGas(100000000000L);

        gasManager = new GasManager(rpcClient, blockchainProperties);
    }

    @Test
    @DisplayName("Should estimate gas limit successfully")
    void shouldEstimateGasLimitSuccessfully() {
        // Given
        String from = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        String to = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        BigInteger value = BigInteger.valueOf(1000000000000000000L);
        String data = "0x";

        BigInteger estimatedGas = BigInteger.valueOf(21000);
        BigInteger expectedGasWithBuffer = BigInteger.valueOf(25200); // 21000 * 1.2

        EthEstimateGas ethEstimateGas = mock(EthEstimateGas.class);
        when(ethEstimateGas.getAmountUsed()).thenReturn(estimatedGas);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethEstimateGas));

        // When & Then
        StepVerifier.create(gasManager.estimateGasLimit(from, to, value, data))
            .assertNext(gasLimit -> {
                assertThat(gasLimit).isEqualTo(expectedGasWithBuffer);
            })
            .verifyComplete();

        verify(rpcClient).executeWithFallback(any());
    }

    @Test
    @DisplayName("Should return default gas when estimation fails")
    void shouldReturnDefaultGasWhenEstimationFails() {
        // Given
        String from = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        String to = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        BigInteger value = BigInteger.valueOf(1000000000000000000L);
        String data = "0x";

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.error(new RuntimeException("RPC error")));

        // When & Then
        StepVerifier.create(gasManager.estimateGasLimit(from, to, value, data))
            .assertNext(gasLimit -> {
                assertThat(gasLimit).isEqualTo(BigInteger.valueOf(21000));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate current gas price with EIP-1559")
    void shouldCalculateCurrentGasPrice() {
        // Given
        BigInteger baseFee = BigInteger.valueOf(20000000000L); // 20 Gwei
        BigInteger priorityFee = BigInteger.valueOf(2000000000L); // 2 Gwei

        // maxFeePerGas = (baseFee * 2) + priorityFee
        // maxFeePerGas = (20 * 2) + 2 = 42 Gwei
        // with 10% buffer = 42 * 1.1 = 46.2 Gwei
        BigInteger expectedMaxFee = BigInteger.valueOf(46200000000L);

        EthBlock ethBlock = mock(EthBlock.class);
        EthBlock.Block block = mock(EthBlock.Block.class);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getBaseFeePerGas()).thenReturn(baseFee);

        EthMaxPriorityFeePerGas ethMaxPriorityFee = mock(EthMaxPriorityFeePerGas.class);
        when(ethMaxPriorityFee.getMaxPriorityFeePerGas()).thenReturn(priorityFee);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethBlock), Mono.just(ethMaxPriorityFee));

        // When & Then
        StepVerifier.create(gasManager.getCurrentGasPrice())
            .assertNext(gasPrice -> {
                assertThat(gasPrice.maxPriorityFeePerGas()).isEqualTo(priorityFee);
                assertThat(gasPrice.maxFeePerGas()).isEqualTo(expectedMaxFee);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should use fallback priority fee when RPC fails")
    void shouldUseFallbackPriorityFeeWhenRpcFails() {
        // Given
        BigInteger baseFee = BigInteger.valueOf(20000000000L); // 20 Gwei
        BigInteger fallbackPriorityFee = BigInteger.valueOf(2000000000L); // 2 Gwei (from config)

        EthBlock ethBlock = mock(EthBlock.class);
        EthBlock.Block block = mock(EthBlock.Block.class);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getBaseFeePerGas()).thenReturn(baseFee);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(
                Mono.just(ethBlock),
                Mono.error(new RuntimeException("Priority fee RPC error"))
            );

        // When & Then
        StepVerifier.create(gasManager.getCurrentGasPrice())
            .assertNext(gasPrice -> {
                assertThat(gasPrice.maxPriorityFeePerGas()).isEqualTo(fallbackPriorityFee);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should not exceed configured max fee per gas")
    void shouldNotExceedConfiguredMaxFee() {
        // Given
        BigInteger veryHighBaseFee = BigInteger.valueOf(100000000000L); // 100 Gwei
        BigInteger priorityFee = BigInteger.valueOf(2000000000L); // 2 Gwei

        // maxFeePerGas would be (100 * 2) + 2 = 202 Gwei with buffer = ~222 Gwei
        // But config max is 100 Gwei, so it should cap at 100 Gwei
        BigInteger configuredMax = BigInteger.valueOf(100000000000L);

        EthBlock ethBlock = mock(EthBlock.class);
        EthBlock.Block block = mock(EthBlock.Block.class);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getBaseFeePerGas()).thenReturn(veryHighBaseFee);

        EthMaxPriorityFeePerGas ethMaxPriorityFee = mock(EthMaxPriorityFeePerGas.class);
        when(ethMaxPriorityFee.getMaxPriorityFeePerGas()).thenReturn(priorityFee);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethBlock), Mono.just(ethMaxPriorityFee));

        // When & Then
        StepVerifier.create(gasManager.getCurrentGasPrice())
            .assertNext(gasPrice -> {
                assertThat(gasPrice.maxFeePerGas()).isLessThanOrEqualTo(configuredMax);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should use fallback base fee when not available")
    void shouldUseFallbackBaseFeeWhenNotAvailable() {
        // Given
        BigInteger priorityFee = BigInteger.valueOf(2000000000L);

        EthBlock ethBlock = mock(EthBlock.class);
        when(ethBlock.getBlock()).thenReturn(null); // Block not available

        EthMaxPriorityFeePerGas ethMaxPriorityFee = mock(EthMaxPriorityFeePerGas.class);
        when(ethMaxPriorityFee.getMaxPriorityFeePerGas()).thenReturn(priorityFee);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethBlock), Mono.just(ethMaxPriorityFee));

        // When & Then
        StepVerifier.create(gasManager.getCurrentGasPrice())
            .assertNext(gasPrice -> {
                // Should use fallback base fee of 20 Gwei
                assertThat(gasPrice.maxPriorityFeePerGas()).isEqualTo(priorityFee);
                assertThat(gasPrice.maxFeePerGas()).isGreaterThan(BigInteger.ZERO);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should apply correct buffer percentage to gas limit")
    void shouldApplyCorrectBufferToGasLimit() {
        // Given
        String from = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        String to = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        BigInteger value = BigInteger.ZERO;
        String data = "0x";

        BigInteger estimatedGas = BigInteger.valueOf(100000);
        // With 20% buffer: 100000 * 1.2 = 120000
        BigInteger expectedGas = BigInteger.valueOf(120000);

        EthEstimateGas ethEstimateGas = mock(EthEstimateGas.class);
        when(ethEstimateGas.getAmountUsed()).thenReturn(estimatedGas);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethEstimateGas));

        // When & Then
        StepVerifier.create(gasManager.estimateGasLimit(from, to, value, data))
            .assertNext(gasLimit -> {
                assertThat(gasLimit).isEqualTo(expectedGas);
            })
            .verifyComplete();
    }
}

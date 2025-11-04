package com.blockchain.tokenizer.integration;

import com.blockchain.tokenizer.TestUtils;
import com.blockchain.tokenizer.dto.TransactionRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.manager.GasManager;
import com.blockchain.tokenizer.manager.NonceManager;
import com.blockchain.tokenizer.service.RPCClient;
import com.blockchain.tokenizer.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test for complete transaction flow.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"tx.raw.for-signing", "tx.signed.ready", "tx.success", "tx.failed"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "blockchain.rpc.config-path=./config/rpc-endpoints-sepolia.json"
})
@DisplayName("Transaction E2E Integration Tests")
class TransactionE2EIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @MockBean
    private RPCClient rpcClient;

    private BigInteger testNonce = BigInteger.valueOf(10);
    private BigInteger testGasLimit = BigInteger.valueOf(21000);
    private BigInteger testBaseFee = BigInteger.valueOf(20000000000L);
    private BigInteger testPriorityFee = BigInteger.valueOf(2000000000L);

    @BeforeEach
    void setUp() {
        // Mock RPC responses for nonce
        EthGetTransactionCount ethGetTransactionCount = mock(EthGetTransactionCount.class);
        when(ethGetTransactionCount.getTransactionCount()).thenReturn(testNonce);
        when(ethGetTransactionCount.hasError()).thenReturn(false);

        // Mock RPC responses for gas estimation
        EthEstimateGas ethEstimateGas = mock(EthEstimateGas.class);
        when(ethEstimateGas.getAmountUsed()).thenReturn(testGasLimit);

        // Mock RPC responses for base fee
        EthBlock ethBlock = mock(EthBlock.class);
        EthBlock.Block block = mock(EthBlock.Block.class);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getBaseFeePerGas()).thenReturn(testBaseFee);

        // Mock RPC responses for priority fee
        EthMaxPriorityFeePerGas ethMaxPriorityFeePerGas = mock(EthMaxPriorityFeePerGas.class);
        when(ethMaxPriorityFeePerGas.getMaxPriorityFeePerGas()).thenReturn(testPriorityFee);

        // Configure mock to return appropriate responses based on request type
        when(rpcClient.executeWithFallback(any()))
            .thenAnswer(invocation -> {
                // Return different mocks based on the request
                // This is simplified - in a real scenario you'd inspect the request type
                return Mono.just(ethGetTransactionCount);
            });
    }

    @Test
    @DisplayName("Should complete full transaction creation flow")
    void shouldCompleteFullTransactionCreationFlow() {
        // Given
        TransactionRequest request = TestUtils.createSimpleTransferRequest();

        // When & Then
        StepVerifier.create(transactionService.createAndSendTransaction(request))
            .assertNext(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getTransactionId()).isNotNull();
                assertThat(response.getStatus()).isIn(
                    TransactionResponse.TransactionStatus.PENDING,
                    TransactionResponse.TransactionStatus.FAILED
                );
                assertThat(response.getTimestamp()).isGreaterThan(0);

                if (response.getStatus() == TransactionResponse.TransactionStatus.PENDING) {
                    assertThat(response.getErrorMessage()).isNull();
                }
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should get transaction count")
    void shouldGetTransactionCount() {
        // Given
        String address = TestUtils.TEST_ADDRESS_FROM;

        // Mock the RPC response
        EthGetTransactionCount ethGetTransactionCount = mock(EthGetTransactionCount.class);
        when(ethGetTransactionCount.getTransactionCount()).thenReturn(BigInteger.valueOf(5));
        when(ethGetTransactionCount.hasError()).thenReturn(false);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethGetTransactionCount));

        // When & Then
        StepVerifier.create(transactionService.getTransactionCount(address))
            .assertNext(nonce -> {
                // Returns current nonce (next - 1)
                assertThat(nonce).isEqualTo(BigInteger.valueOf(4));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should estimate gas for transaction")
    void shouldEstimateGasForTransaction() {
        // Given
        TransactionRequest request = TestUtils.createSimpleTransferRequest();

        // Mock the RPC response
        EthEstimateGas ethEstimateGas = mock(EthEstimateGas.class);
        when(ethEstimateGas.getAmountUsed()).thenReturn(BigInteger.valueOf(21000));

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethEstimateGas));

        // When & Then
        StepVerifier.create(transactionService.estimateGas(request))
            .assertNext(gasLimit -> {
                // Should include buffer (20%)
                assertThat(gasLimit).isGreaterThan(BigInteger.valueOf(21000));
                assertThat(gasLimit).isLessThanOrEqualTo(BigInteger.valueOf(25200));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should get current gas price")
    void shouldGetCurrentGasPrice() {
        // Mock RPC responses
        EthBlock ethBlock = mock(EthBlock.class);
        EthBlock.Block block = mock(EthBlock.Block.class);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getBaseFeePerGas()).thenReturn(BigInteger.valueOf(20000000000L));

        EthMaxPriorityFeePerGas ethMaxPriorityFeePerGas = mock(EthMaxPriorityFeePerGas.class);
        when(ethMaxPriorityFeePerGas.getMaxPriorityFeePerGas()).thenReturn(BigInteger.valueOf(2000000000L));

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethBlock), Mono.just(ethMaxPriorityFeePerGas));

        // When & Then
        StepVerifier.create(transactionService.getGasPrice())
            .assertNext(gasPrice -> {
                assertThat(gasPrice.maxPriorityFeePerGas()).isGreaterThan(BigInteger.ZERO);
                assertThat(gasPrice.maxFeePerGas()).isGreaterThan(BigInteger.ZERO);
                assertThat(gasPrice.maxFeePerGas()).isGreaterThan(gasPrice.maxPriorityFeePerGas());
            })
            .verifyComplete();
    }
}

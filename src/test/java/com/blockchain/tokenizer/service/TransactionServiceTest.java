package com.blockchain.tokenizer.service;

import com.blockchain.tokenizer.TestUtils;
import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
import com.blockchain.tokenizer.dto.TransactionRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.kafka.TransactionKafkaProducer;
import com.blockchain.tokenizer.manager.GasManager;
import com.blockchain.tokenizer.manager.NonceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Tests")
class TransactionServiceTest {

    @Mock
    private NonceManager nonceManager;

    @Mock
    private GasManager gasManager;

    @Mock
    private TransactionKafkaProducer kafkaProducer;

    @Captor
    private ArgumentCaptor<RawTransactionRequest> rawTransactionCaptor;

    private BlockchainProperties blockchainProperties;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        blockchainProperties = new BlockchainProperties();
        blockchainProperties.setChain(new BlockchainProperties.ChainConfig());
        blockchainProperties.getChain().setId(11155111L);
        blockchainProperties.getChain().setNetworkName("sepolia");

        transactionService = new TransactionService(
            nonceManager,
            gasManager,
            kafkaProducer,
            blockchainProperties
        );
    }

    @Test
    @DisplayName("Should create and send transaction successfully")
    void shouldCreateAndSendTransactionSuccessfully() {
        // Given
        TransactionRequest request = TestUtils.createTransactionRequest();

        BigInteger nonce = BigInteger.valueOf(5);
        BigInteger gasLimit = BigInteger.valueOf(21000);
        GasManager.GasPrice gasPrice = new GasManager.GasPrice(
            BigInteger.valueOf(2000000000L),
            BigInteger.valueOf(50000000000L)
        );

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(gasManager.estimateGasLimit(anyString(), anyString(), any(), anyString()))
            .thenReturn(Mono.just(gasLimit));
        when(gasManager.getCurrentGasPrice()).thenReturn(Mono.just(gasPrice));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(transactionService.createAndSendTransaction(request))
            .assertNext(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getTransactionId()).isNotNull();
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
                assertThat(response.getTimestamp()).isGreaterThan(0);
            })
            .verifyComplete();

        verify(nonceManager).getNextNonce(request.getFrom());
        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getFrom()).isEqualTo(request.getFrom());
        assertThat(capturedRequest.getTo()).isEqualTo(request.getTo());
        assertThat(capturedRequest.getValue()).isEqualTo(request.getValue());
        assertThat(capturedRequest.getNonce()).isEqualTo(nonce);
    }

    @Test
    @DisplayName("Should use provided gas parameters when available")
    void shouldUseProvidedGasParametersWhenAvailable() {
        // Given
        TransactionRequest request = TestUtils.createTransactionRequest();
        BigInteger nonce = BigInteger.valueOf(5);

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(transactionService.createAndSendTransaction(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(gasManager, never()).estimateGasLimit(anyString(), anyString(), any(), anyString());
        verify(gasManager, never()).getCurrentGasPrice();
        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getGasLimit()).isEqualTo(request.getGasLimit());
        assertThat(capturedRequest.getMaxPriorityFeePerGas()).isEqualTo(request.getMaxPriorityFeePerGas());
        assertThat(capturedRequest.getMaxFeePerGas()).isEqualTo(request.getMaxFeePerGas());
    }

    @Test
    @DisplayName("Should estimate gas when not provided")
    void shouldEstimateGasWhenNotProvided() {
        // Given
        TransactionRequest request = TestUtils.createSimpleTransferRequest();
        BigInteger nonce = BigInteger.valueOf(3);
        BigInteger estimatedGasLimit = BigInteger.valueOf(21000);
        GasManager.GasPrice gasPrice = new GasManager.GasPrice(
            BigInteger.valueOf(2000000000L),
            BigInteger.valueOf(50000000000L)
        );

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(gasManager.estimateGasLimit(anyString(), anyString(), any(), anyString()))
            .thenReturn(Mono.just(estimatedGasLimit));
        when(gasManager.getCurrentGasPrice()).thenReturn(Mono.just(gasPrice));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(transactionService.createAndSendTransaction(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(gasManager).estimateGasLimit(
            eq(request.getFrom()),
            eq(request.getTo()),
            any(BigInteger.class),
            anyString()
        );
        verify(gasManager).getCurrentGasPrice();
    }

    @Test
    @DisplayName("Should handle transaction creation failure")
    void shouldHandleTransactionCreationFailure() {
        // Given
        TransactionRequest request = TestUtils.createTransactionRequest();

        when(nonceManager.getNextNonce(anyString()))
            .thenReturn(Mono.error(new RuntimeException("Nonce error")));

        // When & Then
        StepVerifier.create(transactionService.createAndSendTransaction(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.FAILED);
                assertThat(response.getErrorMessage()).contains("Nonce error");
            })
            .verifyComplete();

        verify(kafkaProducer, never()).sendForSigning(any());
    }

    @Test
    @DisplayName("Should get transaction count")
    void shouldGetTransactionCount() {
        // Given
        String address = TestUtils.TEST_ADDRESS_FROM;
        BigInteger nextNonce = BigInteger.valueOf(10);
        BigInteger expectedCurrentNonce = BigInteger.valueOf(9);

        when(nonceManager.getNextNonce(address)).thenReturn(Mono.just(nextNonce));

        // When & Then
        StepVerifier.create(transactionService.getTransactionCount(address))
            .assertNext(nonce -> {
                assertThat(nonce).isEqualTo(expectedCurrentNonce);
            })
            .verifyComplete();

        verify(nonceManager).getNextNonce(address);
    }

    @Test
    @DisplayName("Should estimate gas for transaction")
    void shouldEstimateGasForTransaction() {
        // Given
        TransactionRequest request = TestUtils.createSimpleTransferRequest();
        BigInteger expectedGas = BigInteger.valueOf(21000);

        when(gasManager.estimateGasLimit(anyString(), anyString(), any(), anyString()))
            .thenReturn(Mono.just(expectedGas));

        // When & Then
        StepVerifier.create(transactionService.estimateGas(request))
            .assertNext(gasLimit -> {
                assertThat(gasLimit).isEqualTo(expectedGas);
            })
            .verifyComplete();

        verify(gasManager).estimateGasLimit(
            eq(request.getFrom()),
            eq(request.getTo()),
            any(BigInteger.class),
            anyString()
        );
    }

    @Test
    @DisplayName("Should get current gas price")
    void shouldGetCurrentGasPrice() {
        // Given
        GasManager.GasPrice expectedGasPrice = new GasManager.GasPrice(
            BigInteger.valueOf(2000000000L),
            BigInteger.valueOf(50000000000L)
        );

        when(gasManager.getCurrentGasPrice()).thenReturn(Mono.just(expectedGasPrice));

        // When & Then
        StepVerifier.create(transactionService.getGasPrice())
            .assertNext(gasPrice -> {
                assertThat(gasPrice).isEqualTo(expectedGasPrice);
            })
            .verifyComplete();

        verify(gasManager).getCurrentGasPrice();
    }

    @Test
    @DisplayName("Should set default value when not provided")
    void shouldSetDefaultValueWhenNotProvided() {
        // Given
        TransactionRequest request = TransactionRequest.builder()
            .from(TestUtils.TEST_ADDRESS_FROM)
            .to(TestUtils.TEST_ADDRESS_TO)
            .build();

        BigInteger nonce = BigInteger.valueOf(5);
        BigInteger gasLimit = BigInteger.valueOf(21000);
        GasManager.GasPrice gasPrice = new GasManager.GasPrice(
            BigInteger.valueOf(2000000000L),
            BigInteger.valueOf(50000000000L)
        );

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(gasManager.estimateGasLimit(anyString(), anyString(), any(), anyString()))
            .thenReturn(Mono.just(gasLimit));
        when(gasManager.getCurrentGasPrice()).thenReturn(Mono.just(gasPrice));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(transactionService.createAndSendTransaction(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getValue()).isEqualTo(BigInteger.ZERO);
        assertThat(capturedRequest.getData()).isEqualTo("0x");
    }
}

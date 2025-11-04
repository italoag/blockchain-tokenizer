package com.blockchain.tokenizer.service;

import com.blockchain.tokenizer.TestUtils;
import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.dto.ContractDeployRequest;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SmartContractDeployer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmartContractDeployer Tests")
class SmartContractDeployerTest {

    @Mock
    private NonceManager nonceManager;

    @Mock
    private GasManager gasManager;

    @Mock
    private TransactionKafkaProducer kafkaProducer;

    @Captor
    private ArgumentCaptor<RawTransactionRequest> rawTransactionCaptor;

    private BlockchainProperties blockchainProperties;

    private SmartContractDeployer contractDeployer;

    @BeforeEach
    void setUp() {
        blockchainProperties = new BlockchainProperties();
        blockchainProperties.setChain(new BlockchainProperties.ChainConfig());
        blockchainProperties.getChain().setId(11155111L);
        blockchainProperties.getChain().setNetworkName("sepolia");

        contractDeployer = new SmartContractDeployer(
            nonceManager,
            gasManager,
            kafkaProducer,
            blockchainProperties
        );
    }

    @Test
    @DisplayName("Should deploy contract successfully")
    void shouldDeployContractSuccessfully() {
        // Given
        ContractDeployRequest request = TestUtils.createContractDeployRequest();
        BigInteger nonce = BigInteger.valueOf(5);

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(contractDeployer.deployContract(request))
            .assertNext(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getTransactionId()).isNotNull();
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(nonceManager).getNextNonce(request.getFrom());
        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getFrom()).isEqualTo(request.getFrom());
        assertThat(capturedRequest.getTo()).isNull(); // Contract deployment has no 'to' address
        assertThat(capturedRequest.getData()).startsWith("0x");
        assertThat(capturedRequest.getNonce()).isEqualTo(nonce);
    }

    @Test
    @DisplayName("Should estimate gas when not provided")
    void shouldEstimateGasWhenNotProvided() {
        // Given
        ContractDeployRequest request = TestUtils.createContractDeployRequestWithoutGas();
        BigInteger nonce = BigInteger.valueOf(3);
        BigInteger estimatedGas = BigInteger.valueOf(2000000);
        GasManager.GasPrice gasPrice = new GasManager.GasPrice(
            BigInteger.valueOf(2000000000L),
            BigInteger.valueOf(50000000000L)
        );

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(gasManager.estimateGasLimit(anyString(), isNull(), any(), anyString()))
            .thenReturn(Mono.just(estimatedGas));
        when(gasManager.getCurrentGasPrice()).thenReturn(Mono.just(gasPrice));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(contractDeployer.deployContract(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(gasManager).estimateGasLimit(
            eq(request.getFrom()),
            isNull(),
            any(BigInteger.class),
            anyString()
        );
        verify(gasManager).getCurrentGasPrice();
    }

    @Test
    @DisplayName("Should handle contract deployment failure")
    void shouldHandleContractDeploymentFailure() {
        // Given
        ContractDeployRequest request = TestUtils.createContractDeployRequest();

        when(nonceManager.getNextNonce(anyString()))
            .thenReturn(Mono.error(new RuntimeException("Nonce error")));

        // When & Then
        StepVerifier.create(contractDeployer.deployContract(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.FAILED);
                assertThat(response.getErrorMessage()).contains("Nonce error");
            })
            .verifyComplete();

        verify(kafkaProducer, never()).sendForSigning(any());
    }

    @Test
    @DisplayName("Should calculate contract address correctly")
    void shouldCalculateContractAddressCorrectly() {
        // Given
        String deployerAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        BigInteger nonce = BigInteger.valueOf(5);

        // When
        String contractAddress = contractDeployer.calculateContractAddress(deployerAddress, nonce);

        // Then
        assertThat(contractAddress).isNotNull();
        assertThat(contractAddress).startsWith("0x");
        assertThat(contractAddress).hasSize(42); // 0x + 40 hex characters
    }

    @Test
    @DisplayName("Should prepare deployment data with bytecode only")
    void shouldPrepareDeploymentDataWithBytecodeOnly() {
        // Given
        ContractDeployRequest request = ContractDeployRequest.builder()
            .from(TestUtils.TEST_ADDRESS_FROM)
            .bytecode("608060405234801561001057600080fd5b50") // Without 0x prefix
            .build();

        BigInteger nonce = BigInteger.valueOf(3);

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(gasManager.estimateGasLimit(anyString(), isNull(), any(), anyString()))
            .thenReturn(Mono.just(BigInteger.valueOf(2000000)));
        when(gasManager.getCurrentGasPrice()).thenReturn(Mono.just(
            new GasManager.GasPrice(BigInteger.valueOf(2000000000L), BigInteger.valueOf(50000000000L))
        ));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(contractDeployer.deployContract(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getData()).startsWith("0x");
        assertThat(capturedRequest.getData()).contains("608060405234801561001057600080fd5b50");
    }

    @Test
    @DisplayName("Should use provided gas parameters when available")
    void shouldUseProvidedGasParametersWhenAvailable() {
        // Given
        ContractDeployRequest request = TestUtils.createContractDeployRequest();
        BigInteger nonce = BigInteger.valueOf(5);

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(contractDeployer.deployContract(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(gasManager, never()).estimateGasLimit(anyString(), any(), any(), anyString());
        verify(gasManager, never()).getCurrentGasPrice();
        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getGasLimit()).isEqualTo(request.getGasLimit());
        assertThat(capturedRequest.getMaxPriorityFeePerGas()).isEqualTo(request.getMaxPriorityFeePerGas());
        assertThat(capturedRequest.getMaxFeePerGas()).isEqualTo(request.getMaxFeePerGas());
    }

    @Test
    @DisplayName("Should set default value to zero when not provided")
    void shouldSetDefaultValueToZeroWhenNotProvided() {
        // Given
        ContractDeployRequest request = ContractDeployRequest.builder()
            .from(TestUtils.TEST_ADDRESS_FROM)
            .bytecode("0x608060405234801561001057600080fd5b50")
            .gasLimit(BigInteger.valueOf(2000000))
            .maxPriorityFeePerGas(BigInteger.valueOf(2000000000L))
            .maxFeePerGas(BigInteger.valueOf(50000000000L))
            .build();

        BigInteger nonce = BigInteger.valueOf(5);

        when(nonceManager.getNextNonce(anyString())).thenReturn(Mono.just(nonce));
        when(kafkaProducer.sendForSigning(any())).thenReturn(Mono.just(mock(SendResult.class)));

        // When & Then
        StepVerifier.create(contractDeployer.deployContract(request))
            .assertNext(response -> {
                assertThat(response.getStatus()).isEqualTo(TransactionResponse.TransactionStatus.PENDING);
            })
            .verifyComplete();

        verify(kafkaProducer).sendForSigning(rawTransactionCaptor.capture());

        RawTransactionRequest capturedRequest = rawTransactionCaptor.getValue();
        assertThat(capturedRequest.getValue()).isEqualTo(BigInteger.ZERO);
    }
}

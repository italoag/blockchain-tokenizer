package com.blockchain.tokenizer.controller;

import com.blockchain.tokenizer.TestUtils;
import com.blockchain.tokenizer.dto.TransactionRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.manager.GasManager;
import com.blockchain.tokenizer.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for TransactionController.
 */
@WebFluxTest(TransactionController.class)
@DisplayName("TransactionController Integration Tests")
class TransactionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TransactionService transactionService;

    @Test
    @DisplayName("POST /api/v1/transactions should create transaction successfully")
    void shouldCreateTransactionSuccessfully() {
        // Given
        TransactionRequest request = TestUtils.createTransactionRequest();
        TransactionResponse response = TransactionResponse.builder()
            .transactionId("test-tx-id")
            .status(TransactionResponse.TransactionStatus.PENDING)
            .timestamp(System.currentTimeMillis())
            .build();

        when(transactionService.createAndSendTransaction(any()))
            .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isAccepted()
            .expectBody(TransactionResponse.class)
            .value(txResponse -> {
                assert txResponse.getTransactionId().equals("test-tx-id");
                assert txResponse.getStatus() == TransactionResponse.TransactionStatus.PENDING;
            });
    }

    @Test
    @DisplayName("POST /api/v1/transactions should return error when transaction fails")
    void shouldReturnErrorWhenTransactionFails() {
        // Given
        TransactionRequest request = TestUtils.createTransactionRequest();
        TransactionResponse response = TransactionResponse.builder()
            .transactionId("test-tx-id")
            .status(TransactionResponse.TransactionStatus.FAILED)
            .errorMessage("Test error")
            .timestamp(System.currentTimeMillis())
            .build();

        when(transactionService.createAndSendTransaction(any()))
            .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError()
            .expectBody(TransactionResponse.class)
            .value(txResponse -> {
                assert txResponse.getStatus() == TransactionResponse.TransactionStatus.FAILED;
                assert txResponse.getErrorMessage().equals("Test error");
            });
    }

    @Test
    @DisplayName("GET /api/v1/transactions/nonce/{address} should return nonce")
    void shouldReturnNonceForAddress() {
        // Given
        String address = TestUtils.TEST_ADDRESS_FROM;
        BigInteger nonce = BigInteger.valueOf(42);

        when(transactionService.getTransactionCount(anyString()))
            .thenReturn(Mono.just(nonce));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/transactions/nonce/{address}", address)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.address").isEqualTo(address)
            .jsonPath("$.nonce").isEqualTo(42);
    }

    @Test
    @DisplayName("POST /api/v1/transactions/estimate-gas should return estimated gas")
    void shouldReturnEstimatedGas() {
        // Given
        TransactionRequest request = TestUtils.createSimpleTransferRequest();
        BigInteger estimatedGas = BigInteger.valueOf(21000);

        when(transactionService.estimateGas(any()))
            .thenReturn(Mono.just(estimatedGas));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/transactions/estimate-gas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.estimatedGas").isEqualTo(21000);
    }

    @Test
    @DisplayName("GET /api/v1/transactions/gas-price should return gas price")
    void shouldReturnGasPrice() {
        // Given
        GasManager.GasPrice gasPrice = new GasManager.GasPrice(
            BigInteger.valueOf(2000000000L),
            BigInteger.valueOf(50000000000L)
        );

        when(transactionService.getGasPrice())
            .thenReturn(Mono.just(gasPrice));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/transactions/gas-price")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.maxPriorityFeePerGas").isEqualTo(2000000000L)
            .jsonPath("$.maxFeePerGas").isEqualTo(50000000000L);
    }

    @Test
    @DisplayName("POST /api/v1/transactions should validate request body")
    void shouldValidateRequestBody() {
        // Given - Invalid request without required fields
        TransactionRequest invalidRequest = TransactionRequest.builder().build();

        // When & Then
        webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().is4xxClientError();
    }
}

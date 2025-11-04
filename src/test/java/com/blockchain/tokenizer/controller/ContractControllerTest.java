package com.blockchain.tokenizer.controller;

import com.blockchain.tokenizer.TestUtils;
import com.blockchain.tokenizer.dto.ContractDeployRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.service.SmartContractDeployer;
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
import static org.mockito.Mockito.when;

/**
 * Integration tests for ContractController.
 */
@WebFluxTest(ContractController.class)
@DisplayName("ContractController Integration Tests")
class ContractControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SmartContractDeployer contractDeployer;

    @Test
    @DisplayName("POST /api/v1/contracts/deploy should deploy contract successfully")
    void shouldDeployContractSuccessfully() {
        // Given
        ContractDeployRequest request = TestUtils.createContractDeployRequest();
        TransactionResponse response = TransactionResponse.builder()
            .transactionId("test-contract-tx-id")
            .status(TransactionResponse.TransactionStatus.PENDING)
            .timestamp(System.currentTimeMillis())
            .build();

        when(contractDeployer.deployContract(any()))
            .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/contracts/deploy")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isAccepted()
            .expectBody(TransactionResponse.class)
            .value(txResponse -> {
                assert txResponse.getTransactionId().equals("test-contract-tx-id");
                assert txResponse.getStatus() == TransactionResponse.TransactionStatus.PENDING;
            });
    }

    @Test
    @DisplayName("POST /api/v1/contracts/deploy should return error when deployment fails")
    void shouldReturnErrorWhenDeploymentFails() {
        // Given
        ContractDeployRequest request = TestUtils.createContractDeployRequest();
        TransactionResponse response = TransactionResponse.builder()
            .transactionId("test-contract-tx-id")
            .status(TransactionResponse.TransactionStatus.FAILED)
            .errorMessage("Deployment failed")
            .timestamp(System.currentTimeMillis())
            .build();

        when(contractDeployer.deployContract(any()))
            .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/contracts/deploy")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is5xxServerError()
            .expectBody(TransactionResponse.class)
            .value(txResponse -> {
                assert txResponse.getStatus() == TransactionResponse.TransactionStatus.FAILED;
                assert txResponse.getErrorMessage().equals("Deployment failed");
            });
    }

    @Test
    @DisplayName("GET /api/v1/contracts/calculate-address should return contract address")
    void shouldCalculateContractAddress() {
        // Given
        String deployerAddress = TestUtils.TEST_ADDRESS_FROM;
        BigInteger nonce = BigInteger.valueOf(5);
        String expectedContractAddress = "0x8c1eD7e19abAa9f23c476dA86Dc1577F1Ef401f5";

        when(contractDeployer.calculateContractAddress(deployerAddress, nonce))
            .thenReturn(expectedContractAddress);

        // When & Then
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/contracts/calculate-address")
                .queryParam("deployerAddress", deployerAddress)
                .queryParam("nonce", nonce)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.deployerAddress").isEqualTo(deployerAddress)
            .jsonPath("$.nonce").isEqualTo(5)
            .jsonPath("$.contractAddress").isEqualTo(expectedContractAddress);
    }

    @Test
    @DisplayName("POST /api/v1/contracts/deploy should validate request body")
    void shouldValidateRequestBody() {
        // Given - Invalid request without required fields
        ContractDeployRequest invalidRequest = ContractDeployRequest.builder().build();

        // When & Then
        webTestClient.post()
            .uri("/api/v1/contracts/deploy")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().is4xxClientError();
    }
}

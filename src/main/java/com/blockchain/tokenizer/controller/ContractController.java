package com.blockchain.tokenizer.controller;

import com.blockchain.tokenizer.dto.ContractDeployRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.service.SmartContractDeployer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigInteger;

/**
 * REST controller for smart contract operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final SmartContractDeployer contractDeployer;

    /**
     * Deploys a smart contract.
     *
     * POST /api/v1/contracts/deploy
     */
    @PostMapping("/deploy")
    public Mono<ResponseEntity<TransactionResponse>> deployContract(
        @Valid @RequestBody ContractDeployRequest request
    ) {
        log.info("Received contract deployment request from address: {}", request.getFrom());

        return contractDeployer.deployContract(request)
            .map(response -> {
                if (response.getStatus() == TransactionResponse.TransactionStatus.FAILED) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            })
            .doOnSuccess(response -> log.info("Contract deployment initiated with transaction ID: {}",
                response.getBody().getTransactionId()));
    }

    /**
     * Calculates the contract address for a deployment.
     *
     * GET /api/v1/contracts/calculate-address
     */
    @GetMapping("/calculate-address")
    public Mono<ResponseEntity<ContractAddressResponse>> calculateContractAddress(
        @RequestParam String deployerAddress,
        @RequestParam BigInteger nonce
    ) {
        log.debug("Calculating contract address for deployer {} with nonce {}", deployerAddress, nonce);

        return Mono.fromCallable(() -> contractDeployer.calculateContractAddress(deployerAddress, nonce))
            .map(address -> ResponseEntity.ok(new ContractAddressResponse(deployerAddress, nonce, address)))
            .doOnSuccess(response -> log.debug("Calculated contract address: {}",
                response.getBody().contractAddress()));
    }

    // Response DTO
    public record ContractAddressResponse(
        String deployerAddress,
        BigInteger nonce,
        String contractAddress
    ) {}
}

package com.blockchain.tokenizer.controller;

import com.blockchain.tokenizer.dto.TransactionRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.manager.GasManager;
import com.blockchain.tokenizer.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigInteger;

/**
 * REST controller for transaction operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Creates and sends a transaction for signing and broadcasting.
     *
     * POST /api/v1/transactions
     */
    @PostMapping
    public Mono<ResponseEntity<TransactionResponse>> createTransaction(
        @Valid @RequestBody TransactionRequest request
    ) {
        log.info("Received transaction request from {} to {}", request.getFrom(), request.getTo());

        return transactionService.createAndSendTransaction(request)
            .map(response -> {
                if (response.getStatus() == TransactionResponse.TransactionStatus.FAILED) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            })
            .doOnSuccess(response -> log.info("Transaction created with ID: {}",
                response.getBody().getTransactionId()));
    }

    /**
     * Gets the transaction count (nonce) for an address.
     *
     * GET /api/v1/transactions/nonce/{address}
     */
    @GetMapping("/nonce/{address}")
    public Mono<ResponseEntity<NonceResponse>> getTransactionCount(
        @PathVariable String address
    ) {
        log.debug("Getting transaction count for address: {}", address);

        return transactionService.getTransactionCount(address)
            .map(nonce -> ResponseEntity.ok(new NonceResponse(address, nonce)))
            .doOnSuccess(response -> log.debug("Nonce for {}: {}",
                address, response.getBody().nonce()));
    }

    /**
     * Estimates gas for a transaction.
     *
     * POST /api/v1/transactions/estimate-gas
     */
    @PostMapping("/estimate-gas")
    public Mono<ResponseEntity<GasEstimateResponse>> estimateGas(
        @Valid @RequestBody TransactionRequest request
    ) {
        log.debug("Estimating gas for transaction from {} to {}", request.getFrom(), request.getTo());

        return transactionService.estimateGas(request)
            .map(gasLimit -> ResponseEntity.ok(new GasEstimateResponse(gasLimit)))
            .doOnSuccess(response -> log.debug("Estimated gas: {}",
                response.getBody().estimatedGas()));
    }

    /**
     * Gets current gas price recommendations.
     *
     * GET /api/v1/transactions/gas-price
     */
    @GetMapping("/gas-price")
    public Mono<ResponseEntity<GasPriceResponse>> getGasPrice() {
        log.debug("Getting current gas price");

        return transactionService.getGasPrice()
            .map(gasPrice -> ResponseEntity.ok(new GasPriceResponse(
                gasPrice.maxPriorityFeePerGas(),
                gasPrice.maxFeePerGas()
            )))
            .doOnSuccess(response -> log.debug("Current gas price - Priority: {}, Max: {}",
                response.getBody().maxPriorityFeePerGas(),
                response.getBody().maxFeePerGas()));
    }

    // Response DTOs
    public record NonceResponse(String address, BigInteger nonce) {}
    public record GasEstimateResponse(BigInteger estimatedGas) {}
    public record GasPriceResponse(BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {}
}

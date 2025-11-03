package com.blockchain.tokenizer.service;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
import com.blockchain.tokenizer.dto.TransactionRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.kafka.TransactionKafkaProducer;
import com.blockchain.tokenizer.manager.GasManager;
import com.blockchain.tokenizer.manager.NonceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Service for managing the full transaction lifecycle.
 * Orchestrates nonce management, gas estimation, and Kafka communication for signing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final NonceManager nonceManager;
    private final GasManager gasManager;
    private final TransactionKafkaProducer kafkaProducer;
    private final BlockchainProperties blockchainProperties;

    /**
     * Creates and sends a transaction for signing.
     * This method orchestrates the entire flow:
     * 1. Get nonce for sender
     * 2. Estimate gas (if not provided)
     * 3. Get gas price (if not provided)
     * 4. Build raw transaction
     * 5. Send to Kafka for signing
     *
     * @param request transaction request
     * @return Mono of transaction response
     */
    public Mono<TransactionResponse> createAndSendTransaction(TransactionRequest request) {
        String transactionId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        log.info("Creating transaction {} from {} to {}", transactionId, request.getFrom(), request.getTo());

        return buildRawTransaction(request, transactionId, timestamp)
            .flatMap(rawTransaction -> kafkaProducer.sendForSigning(rawTransaction)
                .thenReturn(rawTransaction))
            .map(rawTransaction -> TransactionResponse.builder()
                .transactionId(transactionId)
                .status(TransactionResponse.TransactionStatus.PENDING)
                .timestamp(timestamp)
                .build())
            .doOnSuccess(response -> log.info("Transaction {} sent for signing", transactionId))
            .doOnError(error -> log.error("Failed to create transaction {}: {}",
                transactionId, error.getMessage(), error))
            .onErrorResume(error -> Mono.just(
                TransactionResponse.builder()
                    .transactionId(transactionId)
                    .status(TransactionResponse.TransactionStatus.FAILED)
                    .errorMessage(error.getMessage())
                    .timestamp(timestamp)
                    .build()
            ));
    }

    /**
     * Builds a raw transaction with all required parameters.
     */
    private Mono<RawTransactionRequest> buildRawTransaction(
        TransactionRequest request,
        String transactionId,
        long timestamp
    ) {
        // Parallel fetching of nonce, gas limit, and gas price
        Mono<BigInteger> nonceMono = nonceManager.getNextNonce(request.getFrom());

        Mono<BigInteger> gasLimitMono = request.getGasLimit() != null
            ? Mono.just(request.getGasLimit())
            : gasManager.estimateGasLimit(
                request.getFrom(),
                request.getTo(),
                request.getValue() != null ? request.getValue() : BigInteger.ZERO,
                request.getData() != null ? request.getData() : "0x"
            );

        Mono<GasManager.GasPrice> gasPriceMono = (request.getMaxPriorityFeePerGas() != null
                && request.getMaxFeePerGas() != null)
            ? Mono.just(new GasManager.GasPrice(
                request.getMaxPriorityFeePerGas(),
                request.getMaxFeePerGas()
            ))
            : gasManager.getCurrentGasPrice();

        return Mono.zip(nonceMono, gasLimitMono, gasPriceMono)
            .map(tuple -> {
                BigInteger nonce = tuple.getT1();
                BigInteger gasLimit = tuple.getT2();
                GasManager.GasPrice gasPrice = tuple.getT3();

                return RawTransactionRequest.builder()
                    .transactionId(transactionId)
                    .chainId(blockchainProperties.getChain().getId())
                    .from(request.getFrom())
                    .to(request.getTo())
                    .nonce(nonce)
                    .gasLimit(gasLimit)
                    .maxPriorityFeePerGas(gasPrice.maxPriorityFeePerGas())
                    .maxFeePerGas(gasPrice.maxFeePerGas())
                    .value(request.getValue() != null ? request.getValue() : BigInteger.ZERO)
                    .data(request.getData() != null ? request.getData() : "0x")
                    .timestamp(timestamp)
                    .metadata(request.getMetadata())
                    .build();
            })
            .doOnNext(rawTx -> log.debug("Built raw transaction: nonce={}, gasLimit={}, maxFeePerGas={}",
                rawTx.getNonce(), rawTx.getGasLimit(), rawTx.getMaxFeePerGas()));
    }

    /**
     * Retrieves the current transaction count (nonce) for an address.
     * Useful for debugging and monitoring.
     *
     * @param address the Ethereum address
     * @return Mono of nonce
     */
    public Mono<BigInteger> getTransactionCount(String address) {
        return nonceManager.getNextNonce(address)
            .map(nonce -> nonce.subtract(BigInteger.ONE)) // Return current, not next
            .doOnNext(nonce -> log.debug("Current nonce for {}: {}", address, nonce));
    }

    /**
     * Estimates gas for a transaction without sending it.
     *
     * @param request transaction request
     * @return Mono of estimated gas limit
     */
    public Mono<BigInteger> estimateGas(TransactionRequest request) {
        return gasManager.estimateGasLimit(
            request.getFrom(),
            request.getTo(),
            request.getValue() != null ? request.getValue() : BigInteger.ZERO,
            request.getData() != null ? request.getData() : "0x"
        );
    }

    /**
     * Gets current gas price recommendations.
     *
     * @return Mono of gas price
     */
    public Mono<GasManager.GasPrice> getGasPrice() {
        return gasManager.getCurrentGasPrice();
    }
}

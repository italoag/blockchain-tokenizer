package com.blockchain.tokenizer.kafka;

import com.blockchain.tokenizer.config.KafkaTopicsConfig;
import com.blockchain.tokenizer.dto.SignedTransactionResponse;
import com.blockchain.tokenizer.service.RPCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka consumer for receiving signed transactions and broadcasting them to the blockchain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionKafkaConsumer {

    private final RPCClient rpcClient;
    private final TransactionKafkaProducer kafkaProducer;
    private final KafkaTopicsConfig topicsConfig;

    /**
     * Listens for signed transactions and broadcasts them to the blockchain.
     */
    @KafkaListener(
        topics = "#{kafkaTopicsConfig.txSignedReady}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeSignedTransaction(
        SignedTransactionResponse signedTransaction,
        Acknowledgment acknowledgment
    ) {
        log.info("Received signed transaction: {}", signedTransaction.getTransactionId());

        if (!signedTransaction.isSuccess()) {
            log.error("Received failed signing for transaction {}: {}",
                signedTransaction.getTransactionId(),
                signedTransaction.getErrorMessage());

            kafkaProducer.publishFailure(
                signedTransaction.getTransactionId(),
                "Signing failed: " + signedTransaction.getErrorMessage()
            ).subscribe();

            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        broadcastTransaction(signedTransaction)
            .doOnSuccess(txHash -> {
                log.info("Successfully broadcasted transaction {}: {}",
                    signedTransaction.getTransactionId(), txHash);

                kafkaProducer.publishSuccess(
                    signedTransaction.getTransactionId(),
                    txHash
                ).subscribe();

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            })
            .doOnError(error -> {
                log.error("Failed to broadcast transaction {}: {}",
                    signedTransaction.getTransactionId(),
                    error.getMessage(), error);

                kafkaProducer.publishFailure(
                    signedTransaction.getTransactionId(),
                    "Broadcast failed: " + error.getMessage()
                ).subscribe();

                // Still acknowledge to prevent reprocessing
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            })
            .subscribe();
    }

    /**
     * Broadcasts a signed transaction to the blockchain using RPC fallback.
     */
    private Mono<String> broadcastTransaction(SignedTransactionResponse signedTransaction) {
        String signedRawTx = signedTransaction.getSignedRawTransaction();

        return rpcClient.executeWithFallback(web3j ->
                web3j.ethSendRawTransaction(signedRawTx)
            )
            .map(ethSendTransaction -> {
                if (ethSendTransaction.hasError()) {
                    throw new RuntimeException("Broadcast error: " + ethSendTransaction.getError().getMessage());
                }
                return ethSendTransaction.getTransactionHash();
            })
            .doOnNext(txHash -> log.info("Transaction broadcasted with hash: {}", txHash));
    }
}

package com.blockchain.tokenizer.kafka;

import com.blockchain.tokenizer.config.KafkaTopicsConfig;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka producer for sending raw transactions to the signing service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionKafkaProducer {

    private final KafkaTemplate<String, RawTransactionRequest> kafkaTemplate;
    private final KafkaTopicsConfig topicsConfig;

    /**
     * Sends a raw transaction to the signing service via Kafka.
     *
     * @param rawTransaction the raw transaction request
     * @return Mono of SendResult
     */
    public Mono<SendResult<String, RawTransactionRequest>> sendForSigning(RawTransactionRequest rawTransaction) {
        String topic = topicsConfig.getTxRawForSigning();
        String key = rawTransaction.getTransactionId();

        log.info("Sending raw transaction {} to Kafka topic: {}", key, topic);

        return Mono.fromFuture(
            kafkaTemplate.send(topic, key, rawTransaction)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully sent transaction {} to Kafka. Offset: {}",
                            key, result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send transaction {} to Kafka", key, ex);
                    }
                })
        );
    }

    /**
     * Publishes transaction success event.
     */
    public Mono<Void> publishSuccess(String transactionId, String transactionHash) {
        String topic = topicsConfig.getTxSuccess();

        log.info("Publishing transaction success: {} - {}", transactionId, transactionHash);

        return Mono.fromFuture(
            kafkaTemplate.send(topic, transactionId, transactionHash)
        ).then();
    }

    /**
     * Publishes transaction failure event.
     */
    public Mono<Void> publishFailure(String transactionId, String errorMessage) {
        String topic = topicsConfig.getTxFailed();

        log.warn("Publishing transaction failure: {} - {}", transactionId, errorMessage);

        return Mono.fromFuture(
            kafkaTemplate.send(topic, transactionId, errorMessage)
        ).then();
    }
}

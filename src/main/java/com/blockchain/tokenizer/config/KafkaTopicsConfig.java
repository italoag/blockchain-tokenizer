package com.blockchain.tokenizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Kafka topics used in the transaction signing flow.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicsConfig {

    /**
     * Topic for sending raw transactions to the signing service.
     */
    private String txRawForSigning = "tx.raw.for-signing";

    /**
     * Topic for receiving signed transactions ready for broadcast.
     */
    private String txSignedReady = "tx.signed.ready";

    /**
     * Topic for publishing failed transactions.
     */
    private String txFailed = "tx.failed";

    /**
     * Topic for publishing successful transactions.
     */
    private String txSuccess = "tx.success";
}

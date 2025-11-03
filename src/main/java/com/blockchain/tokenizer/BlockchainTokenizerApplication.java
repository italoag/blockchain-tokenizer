package com.blockchain.tokenizer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main application class for Blockchain Tokenizer Microservice.
 *
 * A reactive microservice for Ethereum blockchain interactions:
 * - Transaction creation and management
 * - Smart contract deployment
 * - Nonce management with Redis
 * - Gas estimation
 * - RPC fallback support
 * - Kafka-based transaction signing workflow
 */
@Slf4j
@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties
public class BlockchainTokenizerApplication {

    public static void main(String[] args) {
        log.info("Starting Blockchain Tokenizer Microservice...");
        SpringApplication.run(BlockchainTokenizerApplication.class, args);
        log.info("Blockchain Tokenizer Microservice started successfully!");
    }
}

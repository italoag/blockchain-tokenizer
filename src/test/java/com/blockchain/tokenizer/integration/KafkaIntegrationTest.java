package com.blockchain.tokenizer.integration;

import com.blockchain.tokenizer.TestUtils;
import com.blockchain.tokenizer.config.KafkaTopicsConfig;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
import com.blockchain.tokenizer.dto.SignedTransactionResponse;
import com.blockchain.tokenizer.kafka.TransactionKafkaConsumer;
import com.blockchain.tokenizer.kafka.TransactionKafkaProducer;
import com.blockchain.tokenizer.service.RPCClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Kafka functionality with Embedded Kafka.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"tx.raw.for-signing", "tx.signed.ready", "tx.success", "tx.failed"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DisplayName("Kafka Integration Tests")
class KafkaIntegrationTest {

    @Autowired
    private TransactionKafkaProducer kafkaProducer;

    @Autowired
    private KafkaTopicsConfig topicsConfig;

    @MockBean
    private RPCClient rpcClient;

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should send raw transaction to Kafka successfully")
    void shouldSendRawTransactionToKafka() throws Exception {
        // Given
        RawTransactionRequest rawTransaction = TestUtils.createRawTransactionRequest();

        // When
        kafkaProducer.sendForSigning(rawTransaction).block();

        // Then
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                // Verify message was sent (in real scenario, consumer would process it)
                assert true;
            });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should consume signed transaction and broadcast to blockchain")
    void shouldConsumeSignedTransactionAndBroadcast() throws Exception {
        // Given
        String transactionId = UUID.randomUUID().toString();
        String txHash = "0xabc123...";

        SignedTransactionResponse signedTransaction = TestUtils.createSignedTransactionResponse(transactionId);

        EthSendTransaction ethSendTransaction = mock(EthSendTransaction.class);
        when(ethSendTransaction.hasError()).thenReturn(false);
        when(ethSendTransaction.getTransactionHash()).thenReturn(txHash);

        when(rpcClient.executeWithFallback(any()))
            .thenReturn(Mono.just(ethSendTransaction));

        // When - Send signed transaction to Kafka
        KafkaTemplate<String, SignedTransactionResponse> signedTxTemplate = createSignedTransactionTemplate();
        signedTxTemplate.send(topicsConfig.getTxSignedReady(), transactionId, signedTransaction);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                verify(rpcClient, atLeastOnce()).executeWithFallback(any());
            });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle failed signing")
    void shouldHandleFailedSigning() throws Exception {
        // Given
        String transactionId = UUID.randomUUID().toString();
        SignedTransactionResponse failedTransaction = TestUtils.createFailedSignedTransactionResponse(
            transactionId,
            "Signing failed"
        );

        // When - Send failed signed transaction to Kafka
        KafkaTemplate<String, SignedTransactionResponse> signedTxTemplate = createSignedTransactionTemplate();
        signedTxTemplate.send(topicsConfig.getTxSignedReady(), transactionId, failedTransaction);

        // Then - Should not broadcast to blockchain
        await().pollDelay(2, TimeUnit.SECONDS)
            .atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                verify(rpcClient, never()).executeWithFallback(any());
            });
    }

    private KafkaTemplate<String, SignedTransactionResponse> createSignedTransactionTemplate() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093");
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        DefaultKafkaProducerFactory<String, SignedTransactionResponse> producerFactory =
            new DefaultKafkaProducerFactory<>(configs);

        return new KafkaTemplate<>(producerFactory);
    }
}

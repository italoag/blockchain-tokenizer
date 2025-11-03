package com.blockchain.tokenizer.examples;

import com.blockchain.tokenizer.dto.RawTransactionRequest;
import com.blockchain.tokenizer.dto.SignedTransactionResponse;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

/**
 * EXAMPLE ONLY: Mock Signer Service
 *
 * This is a demonstration of how an external signing service would work.
 * In production, this would be a separate microservice with secure key management.
 *
 * DO NOT use this in production - it's for testing purposes only!
 */
@Service
public class MockSignerService {

    private final KafkaTemplate<String, SignedTransactionResponse> kafkaTemplate;

    // WARNING: This is a test private key - NEVER use in production!
    private static final String TEST_PRIVATE_KEY =
        "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    public MockSignerService(KafkaTemplate<String, SignedTransactionResponse> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Listens for raw transactions and signs them.
     */
    @KafkaListener(
        topics = "tx.raw.for-signing",
        groupId = "mock-signer-group"
    )
    public void signTransaction(RawTransactionRequest rawTx) {
        try {
            System.out.println("Received transaction for signing: " + rawTx.getTransactionId());

            // Create credentials from private key
            Credentials credentials = Credentials.create(TEST_PRIVATE_KEY);

            // Build Web3j RawTransaction
            RawTransaction transaction = RawTransaction.createTransaction(
                rawTx.getChainId(),
                rawTx.getNonce(),
                rawTx.getGasLimit(),
                rawTx.getTo(),
                rawTx.getValue(),
                rawTx.getData(),
                rawTx.getMaxPriorityFeePerGas(),
                rawTx.getMaxFeePerGas()
            );

            // Sign transaction
            byte[] signedMessage = TransactionEncoder.signMessage(transaction, rawTx.getChainId(), credentials);
            String signedRawTransaction = Numeric.toHexString(signedMessage);

            // Create response
            SignedTransactionResponse response = SignedTransactionResponse.builder()
                .transactionId(rawTx.getTransactionId())
                .signedRawTransaction(signedRawTransaction)
                .signedTimestamp(System.currentTimeMillis())
                .success(true)
                .build();

            // Send to Kafka
            kafkaTemplate.send("tx.signed.ready", rawTx.getTransactionId(), response);

            System.out.println("Transaction signed and sent: " + rawTx.getTransactionId());

        } catch (Exception e) {
            System.err.println("Failed to sign transaction: " + e.getMessage());

            // Send failure response
            SignedTransactionResponse errorResponse = SignedTransactionResponse.builder()
                .transactionId(rawTx.getTransactionId())
                .success(false)
                .errorMessage(e.getMessage())
                .signedTimestamp(System.currentTimeMillis())
                .build();

            kafkaTemplate.send("tx.signed.ready", rawTx.getTransactionId(), errorResponse);
        }
    }
}

/**
 * USAGE:
 *
 * 1. Copy this file to your project
 * 2. Configure it as a separate Spring Boot application or module
 * 3. Run it alongside the main blockchain-tokenizer service
 * 4. It will automatically listen for transactions on 'tx.raw.for-signing'
 * 5. Sign them and publish to 'tx.signed.ready'
 *
 * PRODUCTION CONSIDERATIONS:
 *
 * - Store private keys in a secure vault (AWS KMS, HashiCorp Vault, Azure Key Vault)
 * - Use hardware security modules (HSM) for signing
 * - Implement multi-signature workflows
 * - Add transaction validation and approval workflows
 * - Implement rate limiting and access control
 * - Add comprehensive logging and auditing
 * - Use separate signing infrastructure with network isolation
 */

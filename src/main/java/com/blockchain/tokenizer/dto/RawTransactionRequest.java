package com.blockchain.tokenizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * DTO for raw transaction sent to signing service via Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawTransactionRequest {

    /**
     * Unique transaction identifier for tracking.
     */
    @JsonProperty("transactionId")
    private String transactionId;

    /**
     * Chain ID (e.g., 1 for mainnet, 11155111 for Sepolia).
     */
    @JsonProperty("chainId")
    private long chainId;

    /**
     * Sender address (from).
     */
    @JsonProperty("from")
    private String from;

    /**
     * Recipient address (to).
     */
    @JsonProperty("to")
    private String to;

    /**
     * Transaction nonce.
     */
    @JsonProperty("nonce")
    private BigInteger nonce;

    /**
     * Gas limit for the transaction.
     */
    @JsonProperty("gasLimit")
    private BigInteger gasLimit;

    /**
     * Max priority fee per gas (EIP-1559).
     */
    @JsonProperty("maxPriorityFeePerGas")
    private BigInteger maxPriorityFeePerGas;

    /**
     * Max fee per gas (EIP-1559).
     */
    @JsonProperty("maxFeePerGas")
    private BigInteger maxFeePerGas;

    /**
     * Value to transfer in wei.
     */
    @JsonProperty("value")
    private BigInteger value;

    /**
     * Transaction data (contract call data or empty for simple transfer).
     */
    @JsonProperty("data")
    private String data;

    /**
     * Timestamp when transaction was created.
     */
    @JsonProperty("timestamp")
    private long timestamp;

    /**
     * Optional metadata for tracking purposes.
     */
    @JsonProperty("metadata")
    private String metadata;
}

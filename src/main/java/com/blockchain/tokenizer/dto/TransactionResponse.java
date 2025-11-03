package com.blockchain.tokenizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for transaction response returned to API caller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /**
     * Transaction identifier.
     */
    @JsonProperty("transactionId")
    private String transactionId;

    /**
     * Transaction hash (after broadcast).
     */
    @JsonProperty("transactionHash")
    private String transactionHash;

    /**
     * Transaction status.
     */
    @JsonProperty("status")
    private TransactionStatus status;

    /**
     * Error message if transaction failed.
     */
    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * Timestamp when transaction was created.
     */
    @JsonProperty("timestamp")
    private long timestamp;

    public enum TransactionStatus {
        PENDING,
        SIGNED,
        BROADCASTED,
        CONFIRMED,
        FAILED
    }
}

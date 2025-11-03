package com.blockchain.tokenizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for signed transaction received from signing service via Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedTransactionResponse {

    /**
     * Transaction identifier matching the original request.
     */
    @JsonProperty("transactionId")
    private String transactionId;

    /**
     * Signed raw transaction data (RLP encoded).
     */
    @JsonProperty("signedRawTransaction")
    private String signedRawTransaction;

    /**
     * Timestamp when transaction was signed.
     */
    @JsonProperty("signedTimestamp")
    private long signedTimestamp;

    /**
     * Signature R component (optional, for verification).
     */
    @JsonProperty("signatureR")
    private String signatureR;

    /**
     * Signature S component (optional, for verification).
     */
    @JsonProperty("signatureS")
    private String signatureS;

    /**
     * Signature V component (optional, for verification).
     */
    @JsonProperty("signatureV")
    private Integer signatureV;

    /**
     * Indicates if signing was successful.
     */
    @JsonProperty("success")
    private boolean success;

    /**
     * Error message if signing failed.
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
}

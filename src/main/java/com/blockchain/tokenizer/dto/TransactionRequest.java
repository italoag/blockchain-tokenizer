package com.blockchain.tokenizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * DTO for incoming transaction request from API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    /**
     * Sender address.
     */
    @NotBlank(message = "From address is required")
    @JsonProperty("from")
    private String from;

    /**
     * Recipient address.
     */
    @NotBlank(message = "To address is required")
    @JsonProperty("to")
    private String to;

    /**
     * Value to transfer in wei (optional, defaults to 0).
     */
    @JsonProperty("value")
    private BigInteger value;

    /**
     * Transaction data (optional, for contract calls).
     */
    @JsonProperty("data")
    private String data;

    /**
     * Gas limit (optional, will be estimated if not provided).
     */
    @JsonProperty("gasLimit")
    private BigInteger gasLimit;

    /**
     * Max priority fee per gas (optional, will be calculated if not provided).
     */
    @JsonProperty("maxPriorityFeePerGas")
    private BigInteger maxPriorityFeePerGas;

    /**
     * Max fee per gas (optional, will be calculated if not provided).
     */
    @JsonProperty("maxFeePerGas")
    private BigInteger maxFeePerGas;

    /**
     * Optional metadata for tracking.
     */
    @JsonProperty("metadata")
    private String metadata;
}

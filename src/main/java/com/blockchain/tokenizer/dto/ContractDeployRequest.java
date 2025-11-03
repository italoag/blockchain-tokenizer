package com.blockchain.tokenizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

/**
 * DTO for smart contract deployment request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractDeployRequest {

    /**
     * Deployer address.
     */
    @NotBlank(message = "From address is required")
    @JsonProperty("from")
    private String from;

    /**
     * Contract bytecode (hex encoded).
     */
    @NotBlank(message = "Contract bytecode is required")
    @JsonProperty("bytecode")
    private String bytecode;

    /**
     * Constructor parameters (optional).
     */
    @JsonProperty("constructorParams")
    private List<Object> constructorParams;

    /**
     * Value to send with deployment (optional, defaults to 0).
     */
    @JsonProperty("value")
    private BigInteger value;

    /**
     * Gas limit (optional, will be estimated if not provided).
     */
    @JsonProperty("gasLimit")
    private BigInteger gasLimit;

    /**
     * Max priority fee per gas (optional).
     */
    @JsonProperty("maxPriorityFeePerGas")
    private BigInteger maxPriorityFeePerGas;

    /**
     * Max fee per gas (optional).
     */
    @JsonProperty("maxFeePerGas")
    private BigInteger maxFeePerGas;

    /**
     * Contract ABI for encoding constructor parameters (optional).
     */
    @JsonProperty("abi")
    private String abi;

    /**
     * Optional metadata for tracking.
     */
    @JsonProperty("metadata")
    private String metadata;
}

package com.blockchain.tokenizer.service;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.dto.ContractDeployRequest;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
import com.blockchain.tokenizer.dto.TransactionResponse;
import com.blockchain.tokenizer.kafka.TransactionKafkaProducer;
import com.blockchain.tokenizer.manager.GasManager;
import com.blockchain.tokenizer.manager.NonceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/**
 * Service for deploying and interacting with smart contracts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartContractDeployer {

    private final NonceManager nonceManager;
    private final GasManager gasManager;
    private final TransactionKafkaProducer kafkaProducer;
    private final BlockchainProperties blockchainProperties;

    /**
     * Deploys a smart contract by creating a deployment transaction.
     *
     * @param request contract deployment request
     * @return Mono of transaction response
     */
    public Mono<TransactionResponse> deployContract(ContractDeployRequest request) {
        String transactionId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        log.info("Deploying contract {} from address {}", transactionId, request.getFrom());

        return buildContractDeploymentTransaction(request, transactionId, timestamp)
            .flatMap(rawTransaction -> kafkaProducer.sendForSigning(rawTransaction)
                .thenReturn(rawTransaction))
            .map(rawTransaction -> TransactionResponse.builder()
                .transactionId(transactionId)
                .status(TransactionResponse.TransactionStatus.PENDING)
                .timestamp(timestamp)
                .build())
            .doOnSuccess(response -> log.info("Contract deployment {} sent for signing", transactionId))
            .doOnError(error -> log.error("Failed to deploy contract {}: {}",
                transactionId, error.getMessage(), error))
            .onErrorResume(error -> Mono.just(
                TransactionResponse.builder()
                    .transactionId(transactionId)
                    .status(TransactionResponse.TransactionStatus.FAILED)
                    .errorMessage(error.getMessage())
                    .timestamp(timestamp)
                    .build()
            ));
    }

    /**
     * Builds a contract deployment transaction.
     */
    private Mono<RawTransactionRequest> buildContractDeploymentTransaction(
        ContractDeployRequest request,
        String transactionId,
        long timestamp
    ) {
        // Prepare deployment data: bytecode + constructor parameters
        String deploymentData = prepareDeploymentData(request);

        // Parallel fetching of nonce, gas limit, and gas price
        Mono<BigInteger> nonceMono = nonceManager.getNextNonce(request.getFrom());

        Mono<BigInteger> gasLimitMono = request.getGasLimit() != null
            ? Mono.just(request.getGasLimit())
            : estimateContractDeploymentGas(request, deploymentData);

        Mono<GasManager.GasPrice> gasPriceMono = (request.getMaxPriorityFeePerGas() != null
                && request.getMaxFeePerGas() != null)
            ? Mono.just(new GasManager.GasPrice(
                request.getMaxPriorityFeePerGas(),
                request.getMaxFeePerGas()
            ))
            : gasManager.getCurrentGasPrice();

        return Mono.zip(nonceMono, gasLimitMono, gasPriceMono)
            .map(tuple -> {
                BigInteger nonce = tuple.getT1();
                BigInteger gasLimit = tuple.getT2();
                GasManager.GasPrice gasPrice = tuple.getT3();

                return RawTransactionRequest.builder()
                    .transactionId(transactionId)
                    .chainId(blockchainProperties.getChain().getId())
                    .from(request.getFrom())
                    .to(null)  // Contract deployment has no 'to' address
                    .nonce(nonce)
                    .gasLimit(gasLimit)
                    .maxPriorityFeePerGas(gasPrice.maxPriorityFeePerGas())
                    .maxFeePerGas(gasPrice.maxFeePerGas())
                    .value(request.getValue() != null ? request.getValue() : BigInteger.ZERO)
                    .data(deploymentData)
                    .timestamp(timestamp)
                    .metadata(request.getMetadata())
                    .build();
            })
            .doOnNext(rawTx -> log.debug("Built contract deployment transaction: nonce={}, gasLimit={}",
                rawTx.getNonce(), rawTx.getGasLimit()));
    }

    /**
     * Prepares deployment data by combining bytecode with encoded constructor parameters.
     */
    private String prepareDeploymentData(ContractDeployRequest request) {
        String bytecode = request.getBytecode();

        // Ensure bytecode has 0x prefix
        if (!bytecode.startsWith("0x")) {
            bytecode = "0x" + bytecode;
        }

        // If no constructor parameters, return bytecode as-is
        if (request.getConstructorParams() == null || request.getConstructorParams().isEmpty()) {
            return bytecode;
        }

        // Encode constructor parameters
        try {
            List<Type> constructorParams = encodeConstructorParams(request.getConstructorParams());
            String encodedParams = FunctionEncoder.encodeConstructor(constructorParams);

            // Remove 0x prefix from encoded params and append to bytecode
            if (encodedParams.startsWith("0x")) {
                encodedParams = encodedParams.substring(2);
            }

            return bytecode + encodedParams;
        } catch (Exception e) {
            log.error("Failed to encode constructor parameters", e);
            throw new RuntimeException("Failed to encode constructor parameters: " + e.getMessage(), e);
        }
    }

    /**
     * Encodes constructor parameters.
     * Note: This is a simplified implementation. For production, you would need
     * proper ABI parsing and parameter type resolution.
     */
    private List<Type> encodeConstructorParams(List<Object> params) {
        // This is a placeholder. In a real implementation, you would:
        // 1. Parse the ABI to get constructor parameter types
        // 2. Convert each param to the appropriate Web3j Type
        // For now, we'll just log a warning
        log.warn("Constructor parameter encoding not fully implemented. Returning empty list.");
        return List.of();
    }

    /**
     * Estimates gas for contract deployment.
     */
    private Mono<BigInteger> estimateContractDeploymentGas(
        ContractDeployRequest request,
        String deploymentData
    ) {
        return gasManager.estimateGasLimit(
            request.getFrom(),
            null,  // Contract deployment has no 'to' address
            request.getValue() != null ? request.getValue() : BigInteger.ZERO,
            deploymentData
        ).onErrorReturn(BigInteger.valueOf(2_000_000));  // Default 2M gas for contract deployment
    }

    /**
     * Calculates the contract address that will be created by a deployment transaction.
     * Uses the deployer address and nonce to compute the CREATE address.
     *
     * @param deployerAddress the address deploying the contract
     * @param nonce the transaction nonce
     * @return the computed contract address
     */
    public String calculateContractAddress(String deployerAddress, BigInteger nonce) {
        // Remove 0x prefix if present
        String cleanAddress = Numeric.cleanHexPrefix(deployerAddress);

        // Encode deployer address and nonce using RLP encoding
        // This is simplified; full implementation would use proper RLP encoding
        byte[] addressBytes = Numeric.hexStringToByteArray(cleanAddress);
        byte[] nonceBytes = nonce.toByteArray();

        // Compute Keccak-256 hash
        byte[] hash = Hash.sha3(concatenate(addressBytes, nonceBytes));

        // Take last 20 bytes (160 bits) for the address
        byte[] contractAddress = new byte[20];
        System.arraycopy(hash, hash.length - 20, contractAddress, 0, 20);

        String address = Numeric.toHexString(contractAddress);
        log.debug("Calculated contract address: {} for deployer: {} with nonce: {}",
            address, deployerAddress, nonce);

        return address;
    }

    /**
     * Helper method to concatenate byte arrays.
     */
    private byte[] concatenate(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        byte[] result = new byte[totalLength];
        int offset = 0;

        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        return result;
    }
}

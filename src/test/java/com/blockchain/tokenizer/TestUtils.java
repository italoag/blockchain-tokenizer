package com.blockchain.tokenizer;

import com.blockchain.tokenizer.dto.ContractDeployRequest;
import com.blockchain.tokenizer.dto.RawTransactionRequest;
import com.blockchain.tokenizer.dto.SignedTransactionResponse;
import com.blockchain.tokenizer.dto.TransactionRequest;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for creating test data.
 */
public class TestUtils {

    public static final String TEST_ADDRESS_FROM = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
    public static final String TEST_ADDRESS_TO = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    public static final String TEST_CONTRACT_ADDRESS = "0x6B175474E89094C44Da98b954EedeAC495271d0F";
    public static final BigInteger TEST_VALUE = new BigInteger("1000000000000000000"); // 1 ETH
    public static final BigInteger TEST_GAS_LIMIT = BigInteger.valueOf(21000);
    public static final BigInteger TEST_MAX_PRIORITY_FEE = BigInteger.valueOf(2000000000L); // 2 Gwei
    public static final BigInteger TEST_MAX_FEE = BigInteger.valueOf(50000000000L); // 50 Gwei
    public static final BigInteger TEST_NONCE = BigInteger.ZERO;
    public static final long TEST_CHAIN_ID = 11155111L; // Sepolia

    public static TransactionRequest createTransactionRequest() {
        return TransactionRequest.builder()
            .from(TEST_ADDRESS_FROM)
            .to(TEST_ADDRESS_TO)
            .value(TEST_VALUE)
            .data("0x")
            .gasLimit(TEST_GAS_LIMIT)
            .maxPriorityFeePerGas(TEST_MAX_PRIORITY_FEE)
            .maxFeePerGas(TEST_MAX_FEE)
            .metadata("Test transaction")
            .build();
    }

    public static TransactionRequest createSimpleTransferRequest() {
        return TransactionRequest.builder()
            .from(TEST_ADDRESS_FROM)
            .to(TEST_ADDRESS_TO)
            .value(TEST_VALUE)
            .build();
    }

    public static RawTransactionRequest createRawTransactionRequest() {
        return RawTransactionRequest.builder()
            .transactionId(UUID.randomUUID().toString())
            .chainId(TEST_CHAIN_ID)
            .from(TEST_ADDRESS_FROM)
            .to(TEST_ADDRESS_TO)
            .nonce(TEST_NONCE)
            .gasLimit(TEST_GAS_LIMIT)
            .maxPriorityFeePerGas(TEST_MAX_PRIORITY_FEE)
            .maxFeePerGas(TEST_MAX_FEE)
            .value(TEST_VALUE)
            .data("0x")
            .timestamp(System.currentTimeMillis())
            .metadata("Test raw transaction")
            .build();
    }

    public static SignedTransactionResponse createSignedTransactionResponse(String transactionId) {
        return SignedTransactionResponse.builder()
            .transactionId(transactionId)
            .signedRawTransaction("0xf86c808504a817c800825208945aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed880de0b6b3a76400008025a0...")
            .signedTimestamp(System.currentTimeMillis())
            .success(true)
            .build();
    }

    public static SignedTransactionResponse createFailedSignedTransactionResponse(String transactionId, String errorMessage) {
        return SignedTransactionResponse.builder()
            .transactionId(transactionId)
            .success(false)
            .errorMessage(errorMessage)
            .signedTimestamp(System.currentTimeMillis())
            .build();
    }

    public static ContractDeployRequest createContractDeployRequest() {
        return ContractDeployRequest.builder()
            .from(TEST_ADDRESS_FROM)
            .bytecode("0x608060405234801561001057600080fd5b5060c78061001f6000396000f3fe")
            .constructorParams(List.of())
            .value(BigInteger.ZERO)
            .gasLimit(BigInteger.valueOf(2000000))
            .maxPriorityFeePerGas(TEST_MAX_PRIORITY_FEE)
            .maxFeePerGas(TEST_MAX_FEE)
            .metadata("Test contract deployment")
            .build();
    }

    public static ContractDeployRequest createContractDeployRequestWithoutGas() {
        return ContractDeployRequest.builder()
            .from(TEST_ADDRESS_FROM)
            .bytecode("0x608060405234801561001057600080fd5b5060c78061001f6000396000f3fe")
            .constructorParams(List.of())
            .value(BigInteger.ZERO)
            .build();
    }
}

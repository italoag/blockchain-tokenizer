package com.blockchain.tokenizer.manager;

import com.blockchain.tokenizer.config.BlockchainProperties;
import com.blockchain.tokenizer.service.RPCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.request.Transaction;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Manages gas estimation and pricing for transactions.
 * Calculates optimal gas parameters based on network conditions (EIP-1559).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GasManager {

    private final RPCClient rpcClient;
    private final BlockchainProperties blockchainProperties;

    /**
     * Estimates gas limit for a transaction with buffer.
     *
     * @param from sender address
     * @param to recipient address
     * @param value value to transfer
     * @param data transaction data
     * @return Mono of estimated gas limit
     */
    public Mono<BigInteger> estimateGasLimit(String from, String to, BigInteger value, String data) {
        Transaction transaction = Transaction.createFunctionCallTransaction(
            from,
            null,  // nonce
            null,  // gasPrice
            null,  // gasLimit
            to,
            value,
            data
        );

        return rpcClient.executeWithFallback(web3j -> web3j.ethEstimateGas(transaction))
            .map(ethEstimateGas -> {
                BigInteger estimatedGas = ethEstimateGas.getAmountUsed();
                BigInteger buffered = applyGasLimitBuffer(estimatedGas);

                log.debug("Estimated gas: {}, with buffer: {}", estimatedGas, buffered);
                return buffered;
            })
            .onErrorResume(error -> {
                log.warn("Gas estimation failed, using default: {}", error.getMessage());
                return Mono.just(BigInteger.valueOf(21000)); // Default for simple transfer
            });
    }

    /**
     * Gets current gas price parameters (EIP-1559).
     *
     * @return Mono of GasPrice containing maxPriorityFeePerGas and maxFeePerGas
     */
    public Mono<GasPrice> getCurrentGasPrice() {
        return Mono.zip(
                getBaseFeePerGas(),
                getMaxPriorityFeePerGas()
            )
            .map(tuple -> {
                BigInteger baseFee = tuple.getT1();
                BigInteger priorityFee = tuple.getT2();

                // maxFeePerGas = (baseFee * 2) + maxPriorityFeePerGas
                BigInteger maxFeePerGas = baseFee.multiply(BigInteger.TWO).add(priorityFee);

                // Apply buffer to maxFeePerGas
                maxFeePerGas = applyGasPriceBuffer(maxFeePerGas);

                // Ensure we don't exceed configured maximum
                BigInteger configuredMax = BigInteger.valueOf(blockchainProperties.getGas().getMaxFeePerGas());
                if (maxFeePerGas.compareTo(configuredMax) > 0) {
                    log.warn("Calculated maxFeePerGas {} exceeds configured max {}, using configured value",
                        maxFeePerGas, configuredMax);
                    maxFeePerGas = configuredMax;
                }

                GasPrice gasPrice = new GasPrice(priorityFee, maxFeePerGas);
                log.debug("Current gas price - Priority: {}, Max: {}", priorityFee, maxFeePerGas);

                return gasPrice;
            });
    }

    /**
     * Gets the current base fee per gas from the latest block.
     */
    private Mono<BigInteger> getBaseFeePerGas() {
        return rpcClient.executeWithFallback(web3j ->
                web3j.ethGetBlockByNumber(org.web3j.protocol.core.DefaultBlockParameterName.LATEST, false)
            )
            .map(ethBlock -> {
                if (ethBlock.getBlock() == null || ethBlock.getBlock().getBaseFeePerGas() == null) {
                    log.warn("Base fee not available, using fallback");
                    return BigInteger.valueOf(20_000_000_000L); // 20 Gwei fallback
                }
                return ethBlock.getBlock().getBaseFeePerGas();
            })
            .doOnNext(baseFee -> log.debug("Current base fee: {}", baseFee));
    }

    /**
     * Gets the current max priority fee per gas (miner tip).
     */
    private Mono<BigInteger> getMaxPriorityFeePerGas() {
        return rpcClient.executeWithFallback(web3j -> web3j.ethMaxPriorityFeePerGas())
            .map(ethMaxPriorityFeePerGas -> {
                BigInteger priorityFee = ethMaxPriorityFeePerGas.getMaxPriorityFeePerGas();

                // Use configured value if RPC doesn't provide one
                if (priorityFee == null || priorityFee.equals(BigInteger.ZERO)) {
                    priorityFee = BigInteger.valueOf(blockchainProperties.getGas().getMaxPriorityFeePerGas());
                }

                return priorityFee;
            })
            .onErrorResume(error -> {
                log.warn("Failed to get max priority fee, using configured value: {}", error.getMessage());
                return Mono.just(BigInteger.valueOf(blockchainProperties.getGas().getMaxPriorityFeePerGas()));
            })
            .doOnNext(priorityFee -> log.debug("Max priority fee: {}", priorityFee));
    }

    /**
     * Applies buffer percentage to gas limit.
     */
    private BigInteger applyGasLimitBuffer(BigInteger gasLimit) {
        int bufferPercentage = blockchainProperties.getGas().getLimitBufferPercentage();
        BigDecimal buffer = BigDecimal.valueOf(100 + bufferPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new BigDecimal(gasLimit).multiply(buffer).toBigInteger();
    }

    /**
     * Applies buffer percentage to gas price.
     */
    private BigInteger applyGasPriceBuffer(BigInteger gasPrice) {
        int bufferPercentage = blockchainProperties.getGas().getPriceBufferPercentage();
        BigDecimal buffer = BigDecimal.valueOf(100 + bufferPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new BigDecimal(gasPrice).multiply(buffer).toBigInteger();
    }

    /**
     * Gas price data structure (EIP-1559).
     */
    public record GasPrice(
        BigInteger maxPriorityFeePerGas,
        BigInteger maxFeePerGas
    ) {}
}

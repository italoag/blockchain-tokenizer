# Project Structure

```
blockchain-tokenizer/
├── config/                                    # Configuration files
│   ├── rpc-endpoints.json                    # Mainnet RPC endpoints
│   └── rpc-endpoints-sepolia.json            # Sepolia testnet RPC endpoints
│
├── examples/                                  # Example files
│   ├── api-examples.http                     # REST API examples
│   └── MockSignerService.java                # Example signing service
│
├── src/
│   ├── main/
│   │   ├── java/com/blockchain/tokenizer/
│   │   │   ├── config/                       # Configuration classes
│   │   │   │   ├── BlockchainProperties.java # Blockchain config properties
│   │   │   │   ├── KafkaConfig.java          # Kafka consumer configuration
│   │   │   │   ├── KafkaTopicsConfig.java    # Kafka topics configuration
│   │   │   │   ├── RedisConfig.java          # Redis reactive configuration
│   │   │   │   ├── RPCEndpointsLoader.java   # RPC endpoints loader
│   │   │   │   └── Web3jConfig.java          # Web3j configuration
│   │   │   │
│   │   │   ├── controller/                   # REST Controllers
│   │   │   │   ├── TransactionController.java # Transaction endpoints
│   │   │   │   └── ContractController.java    # Contract deployment endpoints
│   │   │   │
│   │   │   ├── dto/                          # Data Transfer Objects
│   │   │   │   ├── ContractDeployRequest.java
│   │   │   │   ├── RawTransactionRequest.java
│   │   │   │   ├── SignedTransactionResponse.java
│   │   │   │   ├── TransactionRequest.java
│   │   │   │   └── TransactionResponse.java
│   │   │   │
│   │   │   ├── kafka/                        # Kafka producers/consumers
│   │   │   │   ├── TransactionKafkaProducer.java
│   │   │   │   └── TransactionKafkaConsumer.java
│   │   │   │
│   │   │   ├── manager/                      # Business logic managers
│   │   │   │   ├── GasManager.java           # Gas estimation & pricing
│   │   │   │   └── NonceManager.java         # Nonce management with Redis
│   │   │   │
│   │   │   ├── model/                        # Domain models
│   │   │   │   └── RPCEndpointsConfig.java   # RPC endpoints model
│   │   │   │
│   │   │   ├── service/                      # Services
│   │   │   │   ├── RPCClient.java            # RPC client with fallback
│   │   │   │   ├── SmartContractDeployer.java # Contract deployment
│   │   │   │   └── TransactionService.java    # Transaction orchestration
│   │   │   │
│   │   │   └── BlockchainTokenizerApplication.java # Main application
│   │   │
│   │   └── resources/
│   │       ├── META-INF/native-image/
│   │       │   └── reflect-config.json       # GraalVM reflection config
│   │       └── application.yaml              # Application configuration
│   │
│   └── test/
│       ├── java/com/blockchain/tokenizer/
│       │   └── BlockchainTokenizerApplicationTests.java
│       └── resources/
│           └── application-test.yaml         # Test configuration
│
├── Dockerfile                                 # Native image Docker build
├── Dockerfile.jvm                             # JVM Docker build
├── docker-compose.yml                         # Local development stack
├── Makefile                                   # Build automation
├── pom.xml                                    # Maven configuration
├── .gitignore                                 # Git ignore rules
├── README.md                                  # Project documentation
└── PROJECT_STRUCTURE.md                       # This file

```

## Component Descriptions

### Configuration Layer (`config/`)
- **BlockchainProperties**: Central configuration for blockchain interactions
- **KafkaConfig**: Kafka consumer factory and listener configuration
- **KafkaTopicsConfig**: Kafka topic names configuration
- **RedisConfig**: Reactive Redis template configuration
- **RPCEndpointsLoader**: Loads RPC endpoints from external JSON file
- **Web3jConfig**: Web3j and HTTP client configuration

### Controller Layer (`controller/`)
- **TransactionController**: REST API for transaction operations
  - Create transaction
  - Get nonce
  - Estimate gas
  - Get gas price
- **ContractController**: REST API for smart contract operations
  - Deploy contract
  - Calculate contract address

### DTO Layer (`dto/`)
- **ContractDeployRequest**: Request for contract deployment
- **RawTransactionRequest**: Raw transaction sent for signing
- **SignedTransactionResponse**: Signed transaction from signer service
- **TransactionRequest**: API request for transaction creation
- **TransactionResponse**: API response with transaction status

### Kafka Layer (`kafka/`)
- **TransactionKafkaProducer**: Sends transactions for signing
- **TransactionKafkaConsumer**: Receives signed transactions and broadcasts

### Manager Layer (`manager/`)
- **GasManager**: Handles gas estimation and EIP-1559 pricing
  - Estimates gas limit with buffer
  - Calculates maxPriorityFeePerGas and maxFeePerGas
  - Monitors network congestion
- **NonceManager**: Manages transaction nonces with Redis
  - Distributed locking
  - Nonce caching with TTL
  - Automatic nonce recovery

### Service Layer (`service/`)
- **RPCClient**: Web3j client with automatic RPC fallback
  - Prioritizes private endpoints
  - Falls back to public endpoints
  - Circuit breaker integration
  - Retry logic
- **SmartContractDeployer**: Handles contract deployment
  - Builds deployment transactions
  - Encodes constructor parameters
  - Calculates contract addresses
- **TransactionService**: Orchestrates transaction lifecycle
  - Coordinates nonce, gas, and signing
  - Manages transaction state
  - Provides utility methods

## Data Flow

### Transaction Creation Flow
```
Client Request
    ↓
TransactionController
    ↓
TransactionService
    ├─→ NonceManager (get next nonce from Redis)
    ├─→ GasManager (estimate gas & get gas price)
    └─→ Build RawTransactionRequest
    ↓
TransactionKafkaProducer
    ↓
Kafka Topic: tx.raw.for-signing
    ↓
[External Signer Service]
    ↓
Kafka Topic: tx.signed.ready
    ↓
TransactionKafkaConsumer
    ↓
RPCClient (with fallback)
    ↓
Ethereum Network
```

### Contract Deployment Flow
```
Client Request
    ↓
ContractController
    ↓
SmartContractDeployer
    ├─→ Prepare bytecode + constructor params
    ├─→ NonceManager (get nonce)
    ├─→ GasManager (estimate gas)
    └─→ Build RawTransactionRequest
    ↓
[Same as transaction flow]
```

## Key Features by Component

### NonceManager
- ✓ Redis-backed persistence
- ✓ Distributed locking
- ✓ Automatic fallback to blockchain
- ✓ TTL-based caching
- ✓ Concurrent transaction safety

### GasManager
- ✓ EIP-1559 support
- ✓ Dynamic gas estimation
- ✓ Configurable buffers
- ✓ Network congestion awareness
- ✓ Fallback to safe defaults

### RPCClient
- ✓ Multi-endpoint support
- ✓ Automatic failover
- ✓ Priority-based routing
- ✓ Circuit breaker
- ✓ Exponential backoff retry

### Kafka Integration
- ✓ Async signing workflow
- ✓ Manual acknowledgment
- ✓ Error handling
- ✓ Success/failure topics
- ✓ Concurrent processing

## Technology Decisions

| Component | Technology | Reason |
|-----------|-----------|--------|
| Web Framework | Spring WebFlux | Reactive, non-blocking I/O |
| Blockchain | Web3j | Mature Java Ethereum library |
| Caching | Redis (Reactive) | Fast, distributed nonce management |
| Messaging | Kafka | Reliable async communication |
| Circuit Breaker | Resilience4j | Fault tolerance |
| HTTP Client | OkHttp | High performance, connection pooling |
| Serialization | Jackson | Standard JSON library |
| Build Tool | Maven | Enterprise standard |
| Runtime | GraalVM | Native image, fast startup |

## Environment-Specific Configurations

### Development
- Use `application-dev.yaml`
- Local Redis and Kafka
- Public RPC endpoints (Sepolia)
- Verbose logging

### Production
- Use `application-prod.yaml`
- Redis Cluster
- Kafka Cluster
- Private RPC endpoints
- Structured logging
- Metrics export

## Security Considerations

1. **Private Keys**: Never stored in this service
2. **Signing**: Always delegated to external service
3. **RPC Endpoints**: Support both public and private
4. **Input Validation**: All DTOs validated
5. **Rate Limiting**: Consider implementing at API gateway
6. **Monitoring**: Health checks and metrics exposed

## Scalability

- **Horizontal Scaling**: Stateless service design
- **Redis**: Shared nonce state across instances
- **Kafka**: Partitioned topics for parallel processing
- **RPC**: Multiple endpoints with load distribution
- **Connection Pooling**: Efficient resource usage

## Monitoring & Observability

- **Health**: `/actuator/health`
- **Metrics**: `/actuator/prometheus`
- **Logs**: Structured logging with correlation IDs
- **Kafka UI**: Monitor message flow
- **Redis Commander**: View cached nonces

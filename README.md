# Blockchain Tokenizer Microservice

[![Java](https://img.shields.io/badge/Java-24-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![GraalVM](https://img.shields.io/badge/GraalVM-Native-blue.svg)](https://www.graalvm.org/)
[![Web3j](https://img.shields.io/badge/Web3j-4.12-yellow.svg)](https://docs.web3j.io/)

A high-performance, reactive microservice for Ethereum blockchain interactions built with Java 24, Spring Boot 3.5.7, Web3j, and GraalVM Native Image support.

## Features

- **Reactive Architecture**: Fully reactive with Spring WebFlux and Project Reactor
- **Transaction Management**: Complete transaction lifecycle from creation to broadcast
- **Smart Contract Deployment**: Deploy and interact with Ethereum smart contracts
- **Nonce Management**: Redis-backed nonce management for concurrent transaction safety
- **Gas Estimation**: Dynamic gas estimation based on network congestion (EIP-1559)
- **RPC Fallback**: Automatic failover between multiple RPC endpoints
- **Kafka Integration**: Asynchronous transaction signing workflow
- **GraalVM Native Image**: Ultra-fast startup and low memory footprint
- **High Availability**: Circuit breaker, retry logic, and fault tolerance

## Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────┐
│     REST API (WebFlux Controllers)      │
├─────────────────────────────────────────┤
│  • TransactionController                │
│  • ContractController                   │
└──────┬──────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────┐
│         Service Layer                    │
├─────────────────────────────────────────┤
│  • TransactionService                   │
│  • SmartContractDeployer                │
└──────┬──────────────────────────────────┘
       │
       ├───────────┬───────────┬──────────┐
       │           │           │          │
       ▼           ▼           ▼          ▼
┌──────────┐ ┌─────────┐ ┌────────┐ ┌────────┐
│  Nonce   │ │   Gas   │ │  RPC   │ │ Kafka  │
│ Manager  │ │ Manager │ │ Client │ │Producer│
└────┬─────┘ └─────────┘ └────┬───┘ └────┬───┘
     │                         │          │
     ▼                         ▼          ▼
┌─────────┐             ┌──────────┐ ┌────────┐
│  Redis  │             │Ethereum  │ │ Kafka  │
│         │             │   RPC    │ │        │
└─────────┘             └──────────┘ └────────┘
                                          │
                                          ▼
                                    ┌──────────┐
                                    │  Signer  │
                                    │ Service  │
                                    └─────┬────┘
                                          │
                                          ▼
                                    ┌──────────┐
                                    │  Kafka   │
                                    │Consumer  │
                                    └─────┬────┘
                                          │
                                          ▼
                                    ┌──────────┐
                                    │Ethereum  │
                                    │Broadcast │
                                    └──────────┘
```

## Technology Stack

- **Java 24**: Latest Java features with preview features enabled
- **Spring Boot 3.5.7**: Modern Spring framework with reactive support
- **Spring WebFlux**: Reactive web framework
- **Web3j 4.12**: Ethereum Java library
- **Spring Data Redis Reactive**: Reactive Redis integration
- **Reactor Kafka**: Reactive Kafka client
- **Resilience4j**: Circuit breaker and fault tolerance
- **GraalVM Native Image**: Ahead-of-time compilation for native executables
- **Lombok**: Reduce boilerplate code
- **Jackson**: JSON serialization/deserialization
- **OkHttp**: HTTP client for Web3j

## Prerequisites

- **Java 24** (with GraalVM for native image builds)
- **Maven 3.9+**
- **Docker & Docker Compose** (for local development)
- **Redis** (for nonce management)
- **Kafka** (for transaction signing workflow)
- **Ethereum RPC endpoints** (public or private nodes)

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd blockchain-tokenizer
```

### 2. Configure RPC Endpoints

Edit `config/rpc-endpoints.json` or `config/rpc-endpoints-sepolia.json`:

```json
{
  "publicEndpoints": [
    "https://ethereum-sepolia.publicnode.com",
    "https://rpc.ankr.com/eth_sepolia"
  ],
  "privateEndpoints": [
    "http://localhost:8545"
  ]
}
```

### 3. Start Infrastructure with Docker Compose

```bash
docker-compose up -d
```

This will start:
- Redis (port 6379)
- Kafka (port 9092)
- Zookeeper (port 2181)
- Kafka UI (port 8090)
- Redis Commander (port 8081)
- Blockchain Tokenizer service (port 8080)

### 4. Build and Run Locally (JVM)

```bash
# Build
./mvnw clean package

# Run
java -jar target/blockchain-tokenizer-1.0.0-SNAPSHOT.jar
```

### 5. Build Native Image (GraalVM)

```bash
# Requires GraalVM 24+ with native-image installed
./mvnw clean package -Pnative

# Run native executable
./target/blockchain-tokenizer
```

### 6. Build Docker Image

```bash
# JVM-based image (faster build)
docker build -f Dockerfile.jvm -t blockchain-tokenizer:jvm .

# Native image (smaller size, faster startup)
docker build -f Dockerfile -t blockchain-tokenizer:native .
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REDIS_HOST` | Redis hostname | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `REDIS_PASSWORD` | Redis password | (empty) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka servers | localhost:9092 |
| `RPC_ENDPOINTS_CONFIG_PATH` | Path to RPC endpoints JSON | ./config/rpc-endpoints.json |
| `CHAIN_ID` | Ethereum chain ID | 1 (mainnet) |
| `NETWORK_NAME` | Network name | mainnet |

### Application Properties

See `src/main/resources/application.yaml` for full configuration options.

## API Reference

### Base URL
```
http://localhost:8080/api/v1
```

### Endpoints

#### 1. Create Transaction

```bash
POST /transactions
Content-Type: application/json

{
  "from": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "to": "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
  "value": "1000000000000000000",
  "data": "0x"
}
```

Response:
```json
{
  "transactionId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "PENDING",
  "timestamp": 1699564800000
}
```

#### 2. Get Nonce for Address

```bash
GET /transactions/nonce/0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb
```

Response:
```json
{
  "address": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "nonce": 42
}
```

#### 3. Estimate Gas

```bash
POST /transactions/estimate-gas
Content-Type: application/json

{
  "from": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "to": "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
  "value": "1000000000000000000"
}
```

Response:
```json
{
  "estimatedGas": 21000
}
```

#### 4. Get Gas Price

```bash
GET /transactions/gas-price
```

Response:
```json
{
  "maxPriorityFeePerGas": 2000000000,
  "maxFeePerGas": 50000000000
}
```

#### 5. Deploy Contract

```bash
POST /contracts/deploy
Content-Type: application/json

{
  "from": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "bytecode": "0x608060405234801561001057600080fd5b50...",
  "constructorParams": [],
  "value": "0"
}
```

Response:
```json
{
  "transactionId": "123e4567-e89b-12d3-a456-426614174001",
  "status": "PENDING",
  "timestamp": 1699564800000
}
```

#### 6. Calculate Contract Address

```bash
GET /contracts/calculate-address?deployerAddress=0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb&nonce=5
```

Response:
```json
{
  "deployerAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "nonce": 5,
  "contractAddress": "0x8c1eD7e19abAa9f23c476dA86Dc1577F1Ef401f5"
}
```

## Kafka Topics

The microservice uses the following Kafka topics:

| Topic | Purpose | Producer | Consumer |
|-------|---------|----------|----------|
| `tx.raw.for-signing` | Raw transactions for signing | Blockchain Tokenizer | External Signer |
| `tx.signed.ready` | Signed transactions ready for broadcast | External Signer | Blockchain Tokenizer |
| `tx.success` | Successfully broadcasted transactions | Blockchain Tokenizer | Monitoring/Auditing |
| `tx.failed` | Failed transactions | Blockchain Tokenizer | Monitoring/Auditing |

## Transaction Flow

1. **Client** sends transaction request to REST API
2. **TransactionService** orchestrates:
   - Fetches next nonce from **NonceManager** (Redis-backed)
   - Estimates gas using **GasManager**
   - Builds raw transaction
3. **KafkaProducer** sends raw transaction to `tx.raw.for-signing`
4. **External Signer Service** signs the transaction
5. **External Signer Service** publishes to `tx.signed.ready`
6. **KafkaConsumer** receives signed transaction
7. **RPCClient** broadcasts to Ethereum with automatic fallback
8. Success/failure published to respective Kafka topics

## Monitoring

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

### Kafka UI
```
http://localhost:8090
```

### Redis Commander
```
http://localhost:8081
```

## Development

### Running Tests

```bash
./mvnw test
```

### Running with Different Profiles

```bash
# Development
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Hot Reload (Spring Boot DevTools)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

## Production Deployment

### Native Image Benefits

- **Fast Startup**: ~50ms vs ~3s for JVM
- **Low Memory**: ~50MB vs ~300MB for JVM
- **Small Size**: ~80MB vs ~200MB for JVM

### Kubernetes Deployment

Example deployment manifests in `k8s/` directory (create as needed).

### Best Practices

1. **RPC Endpoints**: Use private nodes for production
2. **Redis**: Use Redis Cluster for high availability
3. **Kafka**: Use Kafka cluster with replication factor ≥ 3
4. **Monitoring**: Integrate with Prometheus + Grafana
5. **Logging**: Centralize logs with ELK/Loki
6. **Secrets**: Use Kubernetes Secrets or HashiCorp Vault
7. **Rate Limiting**: Implement API rate limiting
8. **Circuit Breaker**: Already configured via Resilience4j

## Troubleshooting

### Issue: Cannot connect to RPC endpoints

**Solution**: Check `config/rpc-endpoints.json` and verify endpoints are accessible.

### Issue: Nonce issues with concurrent transactions

**Solution**: Ensure Redis is running and accessible. NonceManager uses distributed locks.

### Issue: Kafka consumer not receiving messages

**Solution**: Verify Kafka is running and topics are created. Check consumer group ID.

### Issue: Native image build fails

**Solution**: Ensure GraalVM 24+ is installed with native-image tool. Some Web3j features may need reflection configuration.

## Performance Tuning

### Redis Connection Pool

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

### Kafka Consumer Concurrency

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 50
```

In `KafkaConfig.java`:
```java
factory.setConcurrency(5); // Adjust based on load
```

### Gas Manager Buffers

```yaml
blockchain:
  gas:
    price-buffer-percentage: 10
    limit-buffer-percentage: 20
```

## Security Considerations

- **Never store private keys** in this microservice
- Use external signing service for all transaction signing
- Implement API authentication (JWT, API keys)
- Use TLS for all external communications
- Regular security audits
- Input validation on all endpoints
- Rate limiting to prevent abuse

## Roadmap

- [ ] Support for other EVM chains (Polygon, Arbitrum, Optimism)
- [ ] GraphQL API
- [ ] WebSocket support for real-time updates
- [ ] Transaction batching
- [ ] MEV protection
- [ ] Multi-signature support
- [ ] Contract interaction automation
- [ ] Advanced analytics and reporting

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

[Specify your license here]

## Support

For issues and questions:
- GitHub Issues: [repository-url]/issues
- Email: [support-email]
- Slack: [slack-channel]

## Acknowledgments

Built with:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Web3j](https://docs.web3j.io/)
- [Project Reactor](https://projectreactor.io/)
- [GraalVM](https://www.graalvm.org/)

---

**Made with ❤️ by Principal Blockchain Engineers**

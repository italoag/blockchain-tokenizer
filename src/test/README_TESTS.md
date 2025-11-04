# Test Suite Documentation

## Overview

Comprehensive test suite for the Blockchain Tokenizer microservice with unit tests, integration tests, and end-to-end tests.

## Test Coverage

### Unit Tests

#### Manager Layer
- **NonceManagerTest** - Tests for distributed nonce management with Redis
  - Nonce caching and incrementing
  - Distributed locking
  - Blockchain fallback
  - Address normalization

- **GasManagerTest** - Tests for gas estimation and EIP-1559 pricing
  - Gas limit estimation with buffers
  - Current gas price calculation
  - Fallback mechanisms
  - Max fee capping

#### Service Layer
- **TransactionServiceTest** - Tests for transaction orchestration
  - Transaction creation and sending
  - Gas estimation integration
  - Nonce management integration
  - Error handling

- **SmartContractDeployerTest** - Tests for contract deployment
  - Contract deployment flow
  - Gas estimation for deployments
  - Contract address calculation
  - Constructor parameter encoding

#### Controller Layer
- **TransactionControllerTest** - REST API tests for transactions
  - POST /api/v1/transactions
  - GET /api/v1/transactions/nonce/{address}
  - POST /api/v1/transactions/estimate-gas
  - GET /api/v1/transactions/gas-price
  - Request validation

- **ContractControllerTest** - REST API tests for contracts
  - POST /api/v1/contracts/deploy
  - GET /api/v1/contracts/calculate-address
  - Request validation

### Integration Tests

#### Kafka Integration
- **KafkaIntegrationTest** - Tests with Embedded Kafka
  - Sending raw transactions for signing
  - Consuming signed transactions
  - Broadcasting to blockchain
  - Failed signing handling

#### End-to-End Tests
- **TransactionE2EIntegrationTest** - Complete transaction flow
  - Full transaction creation flow
  - Nonce management with mocked RPC
  - Gas estimation with mocked RPC
  - Gas price retrieval

## Test Technologies

- **JUnit 5** - Test framework
- **Mockito** - Mocking framework
- **AssertJ** - Fluent assertions
- **Reactor Test** - Reactive stream testing with StepVerifier
- **Spring Boot Test** - Spring integration testing
- **WebTestClient** - Reactive REST API testing
- **Embedded Kafka** - In-memory Kafka for integration tests
- **Embedded Redis** - In-memory Redis for integration tests
- **Awaitility** - Async/concurrent testing utilities

## Running Tests

### All Tests
```bash
# Using Maven
./mvnw test

# Using Make
make test
```

### Specific Test Class
```bash
./mvnw test -Dtest=NonceManagerTest
```

### Specific Test Method
```bash
./mvnw test -Dtest=NonceManagerTest#shouldGetNextNonceFromCache
```

### Integration Tests Only
```bash
./mvnw test -Dtest=*IntegrationTest
```

### Unit Tests Only
```bash
./mvnw test -Dtest=*Test -Dtest=!*IntegrationTest
```

### With Coverage Report
```bash
./mvnw test jacoco:report

# View report at: target/site/jacoco/index.html
```

### Skip Tests
```bash
./mvnw package -DskipTests
```

## Test Structure

```
src/test/java/com/blockchain/tokenizer/
├── config/
│   └── EmbeddedRedisTestConfiguration.java    # Embedded Redis config
├── controller/
│   ├── TransactionControllerTest.java         # Transaction API tests
│   └── ContractControllerTest.java            # Contract API tests
├── integration/
│   ├── KafkaIntegrationTest.java              # Kafka integration tests
│   └── TransactionE2EIntegrationTest.java     # End-to-end tests
├── manager/
│   ├── NonceManagerTest.java                  # Nonce manager unit tests
│   └── GasManagerTest.java                    # Gas manager unit tests
├── service/
│   ├── TransactionServiceTest.java            # Transaction service tests
│   └── SmartContractDeployerTest.java         # Contract deployer tests
├── TestUtils.java                              # Test data utilities
└── BlockchainTokenizerApplicationTests.java   # Application context test
```

## Test Data

The `TestUtils` class provides factory methods for creating test data:

```java
// Create transaction request
TransactionRequest request = TestUtils.createTransactionRequest();

// Create simple transfer
TransactionRequest transfer = TestUtils.createSimpleTransferRequest();

// Create raw transaction
RawTransactionRequest rawTx = TestUtils.createRawTransactionRequest();

// Create signed transaction response
SignedTransactionResponse signed = TestUtils.createSignedTransactionResponse("tx-id");

// Create contract deploy request
ContractDeployRequest deploy = TestUtils.createContractDeployRequest();
```

## Test Annotations

### Common Annotations

```java
@ExtendWith(MockitoExtension.class)  // Mockito support
@DisplayName("Test Description")      // Human-readable test name
@Test                                 // JUnit test method
@BeforeEach                          // Setup before each test
@AfterEach                           // Cleanup after each test
@Timeout(value = 10, unit = TimeUnit.SECONDS)  // Test timeout
```

### Spring Test Annotations

```java
@SpringBootTest                      // Full Spring context
@WebFluxTest(Controller.class)       // WebFlux slice test
@MockBean                            // Mock Spring bean
@Autowired                           // Inject bean
@EmbeddedKafka                       // Start embedded Kafka
@TestPropertySource                  // Override properties
```

## Writing Tests

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Example Tests")
class ExampleTest {

    @Mock
    private Dependency dependency;

    private ServiceUnderTest service;

    @BeforeEach
    void setUp() {
        service = new ServiceUnderTest(dependency);
    }

    @Test
    @DisplayName("Should do something")
    void shouldDoSomething() {
        // Given
        when(dependency.doSomething()).thenReturn(Mono.just("result"));

        // When
        Mono<String> result = service.performAction();

        // Then
        StepVerifier.create(result)
            .expectNext("result")
            .verifyComplete();

        verify(dependency).doSomething();
    }
}
```

### Integration Test Example

```java
@SpringBootTest
@EmbeddedKafka(topics = {"test-topic"})
@DisplayName("Integration Tests")
class IntegrationTest {

    @Autowired
    private Service service;

    @MockBean
    private ExternalDependency externalDep;

    @Test
    @DisplayName("Should integrate components")
    void shouldIntegrateComponents() {
        // Given
        when(externalDep.call()).thenReturn(Mono.just("response"));

        // When
        var result = service.execute();

        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response).isNotNull();
            })
            .verifyComplete();
    }
}
```

### WebFlux Controller Test Example

```java
@WebFluxTest(MyController.class)
@DisplayName("Controller Tests")
class ControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private Service service;

    @Test
    @DisplayName("Should return OK")
    void shouldReturnOk() {
        // Given
        when(service.getData()).thenReturn(Mono.just(new Data()));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/data")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Data.class)
            .value(data -> assertThat(data).isNotNull());
    }
}
```

## Reactor Testing with StepVerifier

```java
// Test successful completion
StepVerifier.create(mono)
    .expectNext(expectedValue)
    .verifyComplete();

// Test multiple values
StepVerifier.create(flux)
    .expectNext(value1)
    .expectNext(value2)
    .expectNext(value3)
    .verifyComplete();

// Test with assertion
StepVerifier.create(mono)
    .assertNext(value -> {
        assertThat(value.getField()).isEqualTo("expected");
    })
    .verifyComplete();

// Test error
StepVerifier.create(mono)
    .expectErrorMessage("Expected error message")
    .verify();

// Test with timeout
StepVerifier.create(mono)
    .expectNext(expectedValue)
    .expectComplete()
    .verify(Duration.ofSeconds(5));
```

## Async Testing with Awaitility

```java
import static org.awaitility.Awaitility.*;

// Wait for condition
await()
    .atMost(5, TimeUnit.SECONDS)
    .untilAsserted(() -> {
        verify(mock).methodWasCalled();
    });

// Poll with delay
await()
    .pollDelay(2, TimeUnit.SECONDS)
    .atMost(10, TimeUnit.SECONDS)
    .until(() -> repository.count() > 0);
```

## Best Practices

1. **Naming**: Use descriptive test names that explain what is being tested
2. **Arrange-Act-Assert**: Follow AAA pattern (Given-When-Then)
3. **Isolation**: Each test should be independent
4. **Cleanup**: Always clean up resources in @AfterEach
5. **Mocking**: Mock external dependencies, not internal logic
6. **Coverage**: Aim for >80% code coverage
7. **Fast**: Keep unit tests fast (<100ms each)
8. **Readable**: Tests are documentation - make them clear
9. **One Assertion**: Test one thing per test method
10. **Reactive Testing**: Always use StepVerifier for reactive streams

## Troubleshooting

### Embedded Kafka Not Starting
```bash
# Ensure port 9093/9094 is free
lsof -ti:9093 | xargs kill -9
lsof -ti:9094 | xargs kill -9
```

### Embedded Redis Not Starting
```bash
# Ensure port 6379 is free
lsof -ti:6379 | xargs kill -9

# Or change test port in configuration
```

### Tests Timeout
```bash
# Increase timeout
@Timeout(value = 30, unit = TimeUnit.SECONDS)

# Or increase default timeout in pom.xml
```

### Context Loading Fails
```bash
# Check application-test.yaml
# Ensure all mocked beans are defined
# Check @MockBean annotations
```

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run Tests
  run: ./mvnw test

- name: Generate Coverage Report
  run: ./mvnw jacoco:report

- name: Upload Coverage
  uses: codecov/codecov-action@v3
```

### Jenkins Example
```groovy
stage('Test') {
    steps {
        sh './mvnw test'
    }
    post {
        always {
            junit 'target/surefire-reports/*.xml'
            jacoco execPattern: 'target/jacoco.exec'
        }
    }
}
```

## Test Coverage Goals

- **Overall**: ≥ 80%
- **Service Layer**: ≥ 90%
- **Manager Layer**: ≥ 90%
- **Controller Layer**: ≥ 85%
- **Integration**: All critical paths

## Future Improvements

- [ ] Add performance tests
- [ ] Add contract tests with Pact
- [ ] Add mutation testing with PIT
- [ ] Add architecture tests with ArchUnit
- [ ] Add load tests with Gatling
- [ ] Increase integration test coverage
- [ ] Add TestContainers for more realistic integration tests

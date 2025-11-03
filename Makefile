.PHONY: help build build-native test run run-docker clean docker-build docker-push up down logs

# Variables
APP_NAME := blockchain-tokenizer
VERSION := 1.0.0-SNAPSHOT
DOCKER_IMAGE := $(APP_NAME)
DOCKER_TAG := latest

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## Build the application (JVM)
	./mvnw clean package -DskipTests

build-native: ## Build native image with GraalVM
	./mvnw clean package -Pnative -DskipTests

test: ## Run tests
	./mvnw test

run: ## Run the application locally
	./mvnw spring-boot:run

run-docker: ## Run the application in Docker
	docker run -p 8080:8080 \
		-e REDIS_HOST=host.docker.internal \
		-e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
		$(DOCKER_IMAGE):$(DOCKER_TAG)

clean: ## Clean build artifacts
	./mvnw clean
	rm -rf target/

docker-build: ## Build Docker image (JVM)
	docker build -f Dockerfile.jvm -t $(DOCKER_IMAGE):$(DOCKER_TAG) .

docker-build-native: ## Build Docker image (Native)
	docker build -f Dockerfile -t $(DOCKER_IMAGE):native .

docker-push: ## Push Docker image to registry
	docker push $(DOCKER_IMAGE):$(DOCKER_TAG)

up: ## Start all services with docker-compose
	docker-compose up -d

down: ## Stop all services
	docker-compose down

logs: ## Show logs from docker-compose
	docker-compose logs -f blockchain-tokenizer

infra-up: ## Start only infrastructure (Redis, Kafka)
	docker-compose up -d redis kafka zookeeper

infra-down: ## Stop infrastructure
	docker-compose stop redis kafka zookeeper

verify: ## Verify the build without packaging
	./mvnw verify

format: ## Format code
	./mvnw spotless:apply

check: ## Check code style
	./mvnw spotless:check

deps: ## Download dependencies
	./mvnw dependency:resolve

tree: ## Show dependency tree
	./mvnw dependency:tree

health: ## Check application health
	curl http://localhost:8080/actuator/health

metrics: ## Show Prometheus metrics
	curl http://localhost:8080/actuator/prometheus

kafka-topics: ## List Kafka topics
	docker exec -it blockchain-kafka kafka-topics --bootstrap-server localhost:9092 --list

kafka-create-topics: ## Create Kafka topics
	docker exec -it blockchain-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic tx.raw.for-signing --partitions 3 --replication-factor 1
	docker exec -it blockchain-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic tx.signed.ready --partitions 3 --replication-factor 1
	docker exec -it blockchain-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic tx.success --partitions 3 --replication-factor 1
	docker exec -it blockchain-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic tx.failed --partitions 3 --replication-factor 1

redis-cli: ## Connect to Redis CLI
	docker exec -it blockchain-redis redis-cli

all: clean build test ## Clean, build, and test

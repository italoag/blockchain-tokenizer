# Multi-stage Dockerfile for GraalVM Native Image

# Stage 1: Build Native Image with GraalVM
FROM ghcr.io/graalvm/graalvm-community:24 AS builder

# Install native-image tool
RUN gu install native-image

# Set working directory
WORKDIR /build

# Copy Maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build native image
RUN ./mvnw clean package -Pnative -DskipTests

# Stage 2: Runtime image
FROM debian:bookworm-slim

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    ca-certificates \
    libstdc++6 && \
    rm -rf /var/lib/apt/lists/*

# Create app user
RUN useradd -r -u 1001 -g root appuser

# Set working directory
WORKDIR /app

# Copy native executable from builder
COPY --from=builder /build/target/blockchain-tokenizer /app/blockchain-tokenizer

# Copy configuration files
COPY --chown=appuser:root config/ /app/config/

# Change ownership
RUN chown -R appuser:root /app

# Switch to app user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["/app/blockchain-tokenizer"]

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Concurrency Control PoC: A performance comparison project for 4 different concurrency control methods in high-traffic inventory deduction scenarios. Compares Pessimistic Lock, Optimistic Lock, Redis Distributed Lock, and Redis Lua Script using k6 load testing.

**Tech Stack:** Spring Boot 4.0.1, Java 21, MySQL 8.0, Redis 7.0, Gradle

## Build & Run Commands

### Infrastructure Setup
```bash
# Start MySQL and Redis containers
make up

# Check container health (wait for "Healthy" status)
make ps

# Stop containers
make down

# Reset stock to 100 for testing
make reset

# Clean up containers and volumes
make clean
```

### Application Development
```bash
# Run application (requires infrastructure running)
./gradlew bootRun

# Run all tests (includes ArchUnit architecture tests)
./gradlew test

# Run tests for a specific class
./gradlew test --tests ClassName

# Build without tests
./gradlew build -x test

# Clean build artifacts
./gradlew clean
```

### Database & Redis Access
```bash
# Connect to MySQL
make mysql
# Credentials: app_user/app_password, database: concurrency_db

# Connect to Redis CLI
make redis
```

## Architecture & Design Principles

### Strict Layered Architecture

This project enforces a **strict layered architecture** validated by ArchUnit tests at runtime:

```
controller/    → Presentation Layer (REST API, DTO only)
service/       → Application Layer (business logic, transactions)
domain/        → Domain Layer (entities, core business rules)
repository/    → Infrastructure Layer (JPA repositories)
```

**Critical Rules Enforced by ArchUnit:**
- Controllers MUST use DTOs, never domain entities directly
- Controllers CANNOT access repositories directly (must go through service)
- Domain layer has NO dependencies on other layers (pure POJO)
- Service layer is the only bridge between controller and repository

**Location:** Architecture rules are defined in `src/test/java/com/concurrency/poc/architecture/LayeredArchitectureTest.java`

Running `./gradlew test` will fail if any layer boundary violations are detected.

### DTO Pattern Requirement

When creating new endpoints:
1. Create request/response DTOs in the controller package
2. Service methods accept and return DTOs or domain objects
3. Controllers must never expose domain entities in HTTP responses
4. Use Java Records for concise DTO definitions

## Concurrency Control Implementations

This project will implement 4 different approaches (to be implemented in future sprints):

1. **Pessimistic Lock:** JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`
2. **Optimistic Lock:** JPA `@Version` annotation with application-level retry logic
3. **Redis Distributed Lock:** Redisson `RLock` with Pub/Sub for efficient waiting
4. **Redis Lua Script:** Atomic script execution without lock acquisition overhead

Each implementation targets the same use case: safely decrementing stock inventory under high concurrency.

## Database Configuration

- **MySQL Port:** 13306 (mapped from container's 3306)
- **Redis Port:** 16379 (mapped from container's 6379)
- **Connection Strings:** Configured in `src/main/resources/application.yml`
- **Schema Management:** JPA `ddl-auto: validate` (schema must be created manually or via init scripts)
- **Init Scripts:** Place SQL files in `mysql-init/` directory (auto-executed on first startup)

## Testing Strategy

- **Unit Tests:** Standard JUnit 5 tests for business logic
- **Architecture Tests:** ArchUnit validates layer boundaries on every test run
- **Load Tests:** k6 scripts for performance benchmarking (TPS and latency metrics)
- **Integration Tests:** Test with real MySQL/Redis using Testcontainers (if added later)

## Key Documentation

- **System Overview:** `docs/architecture/system-overview.md` - C4 diagrams and sequence flows
- **Application Architecture:** `docs/architecture/application.md` - Package structure and ArchUnit rules
- **ADRs:** `docs/adr/` - Architecture decision records explaining design choices
  - ADR-005: Why Layered Architecture over Hexagonal/Clean Architecture

## Development Notes

- **Java Version:** 21 LTS (enables Records, Virtual Threads)
- **SQL Logging:** Enabled by default (`spring.jpa.show-sql: true`)
- **Health Checks:** Wait for Docker containers to show "Healthy" before running application
- **Stock Reset:** Use `make reset` between load test runs to restore initial state

# Workspace Constitution & Foundational Coding Principles

This document establishes the project's foundational coding principles, architectural boundaries, style constraints, and security standards for the `ecommerce-platform` Java Spring Boot microservices ecosystem.

---

## 1. Architectural Boundaries (DDD & Isolation)

*   **Domain-Driven Design (DDD)**: Enforce Domain-Driven Design principles. Business logic must be encapsulated within the Domain model or the Service layer, not in Controllers or Entities directly if it breaks isolation.
*   **Absolute Layer Isolation**:
    *   **Controller Layer**: Handles REST/HTTP protocols, request validation, authentication checks, and routes to appropriate service methods. Must never execute database operations or business transactions directly.
    *   **Service Layer**: Encapsulates core business workflows, transaction boundaries, orchestration, cache coordination, and event publishing. Completely independent of HTTP or low-level transport mechanisms.
    *   **Repository/Adapter Layer**: Handles database/persistence interactions (MongoDB, PostgreSQL, Redis, Elasticsearch). Serves purely to retrieve and store records/documents.
*   **Layer Flow Constraint**: Controller $\rightarrow$ Service $\rightarrow$ Repository/Adapter. Strictly forbid upward layer reference or bypass (e.g., Controllers talking to Repositories directly).

---

## 2. Technical Stack Rules & Best Practices

### A. Data Management & Persistence Isolation
*   **Database Types**: Keep PostgreSQL (relational) and MongoDB (document-based) strictly decoupled.
    *   **Relational Entities**: Use standard JPA/Hibernate mapping annotations, Liquibase or Flyway for schema migrations.
    *   **Document Structures**: Leverage MongoDB collections for hierarchical, aggregate, or high-volume event/document storage.
*   **JSONB & Map Attributes**:
    *   For PostgreSQL: Use JSONB columns with explicit GIN indexing (`@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)`) for structured dynamic data.
    *   For MongoDB: Use `Map<String, Object>` fields annotated with `@org.springframework.data.mongodb.core.index.WildcardIndexed` to create high-performance wildcard indexes (`attributes.$**`) on nested attributes.

### B. Data Transfer (DTOs & Immutability)
*   **DTO Isolation**: Database entities/documents must **never** be exposed directly via REST controllers, Kafka events, or external messaging brokers.
*   **Immutable DTOs**: Define DTOs as immutable structures using Lombok `@Value` or standard Java `record` declarations. Avoid mutable properties in transfer payloads.
*   **Mapping**: Use **MapStruct** for lightning-fast, compile-time checked translations between Entities/Documents and DTOs.

### C. Transaction Management & Thread Safety
*   **Transactional Boundaries**: Mark service layer methods needing relational ACID guarantees with `@Transactional(transactionManager = "transactionManager")`.
*   **No Cross-DB Transactions**: Avoid distributed 2PC transactions across MongoDB and PostgreSQL. Utilize the **Transactional Outbox Pattern** (e.g., embedded document outbox inside MongoDB aggregates) with atomic writes for eventual consistency.
*   **Concurrency**: Avoid local locking mechanics in distributed environments. Use **ShedLock + Redis** or Redisson distributed locks to guard shared actions (e.g., cron schedulers, unique resource creations).

---

## 3. Style, Quality, and Security Constraints

*   **Type Safety**: Avoid using raw types, untyped maps (where types are known), or wildcard objects in signature contracts unless explicitly designed for heterogeneous extensible data structures (like `attributes`).
*   **Error Handling**: Centralize REST exception handling using `@RestControllerAdvice`. Throw clean domain exception classes (e.g., `ResourceNotFoundException`, `ForbiddenOperationException`) and map them to standard RFC-7807 problem details responses.
*   **Logging**: Use SLF4J `@Slf4j` for clean logging. Never print stack traces directly (`e.printStackTrace()`) or use standard console output (`System.out.println`).
*   **Security Standards**: Always assert JWT claims or user principal contexts in Controllers. Use `CurrentUser.requireId()` to establish active tenancy or ownership validation before performing database updates.

---

## 4. Testing Rigor & Gating

*   **Testing Technologies**: Standardize on **JUnit 5**, **Mockito** (`MockitoExtension.class`), and **AssertJ** for expressive assertions.
*   **Coverage Metric**: Enforce a minimum of **80% code coverage** on all new business logic/service components.
*   **Verification Cycles**: Every implementation iteration must pass local compilation (`./mvnw compile`) and test suites (`./mvnw test`) before merge eligibility.

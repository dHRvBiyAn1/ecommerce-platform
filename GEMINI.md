# Gemini Code Quality and Spec-Driven Development Directive

This directory is an enterprise Java Spring Boot Microservices ecosystem governed by Spec-Driven Development (SDD) principles. Adhere strictly to the guidelines defined in this file.

---

## 1. Automated Spec-Driven Development (SDD) Lifecycle
No codebase modifications or code additions should be created unless the following gates are cleared:
1.  **Memory/Constitution**: Align structural designs with principles declared in [.specify/memory/constitution.md](file:///.specify/memory/constitution.md).
2.  **Vision/Specification**: Author or verify `spec.md` with explicit features and acceptance criteria first.
3.  **Plan/Architecture**: Draft the execution flow, database DDLs, mappings, caching levels, and indices in `plan.md`.
4.  **Tasks/Checklist**: Compile testable, atomic, modular items in `tasks.md` before coding.

---

## 2. Java Spring Boot Quality & Architectural Standards

### A. Architectural Layer Isolation
*   **Domain-Driven Design (DDD)** isolates Controller $\rightarrow$ Service $\rightarrow$ Repository/Adapter modules.
*   Direct interaction between Controllers and Repositories is strictly forbidden.
*   Enforce transactional isolation boundary at the Service layer (`@Transactional`).

### B. Persistent Stores (PostgreSQL and MongoDB)
*   **PostgreSQL**: Utilize Flyway/Liquibase migration scripts. Leverage GIN-indexed `jsonb` columns for polymorphic payloads using `@JdbcTypeCode(SqlTypes.JSON)`.
*   **MongoDB**: Utilize document aggregation models. Enable flexible key-value properties using `Map<String, Object>` fields marked with `@WildcardIndexed`.

### C. Data Transfer (DTOs)
*   Raw entities/documents must never escape database adapter boundaries.
*   Utilize MapStruct for mapping; DTO classes must be fully immutable using Lombok `@Value` or Java `records`.

### D. Automated Testing & Verification
*   Utilize JUnit 5 and Mockito. All services must maintain a **>80% coverage** rate.
*   Run local test validation:
    ```bash
    ./mvnw clean test
    ```

---

## 3. Knowledge Graph Integration
*   Ensure that any filesystem mutation is followed by a re-index of the knowledge graph:
    ```bash
    graphify build
    ```
*   Use local graph queries to map dependency trees and inspect the blast-radius of database updates.

# Design & Implementation Plan: [Feature Name]

## 1. System Architecture & Components
*   **Component Diagram / Logic Flow**: Describe the component layers (Controllers, Services, Repositories, Adapters) or async flows (Kafka consumers/publishers).
*   **Layer Interactions**: Detail exactly how layers request data and handle outputs.

## 2. API Contracts & Communication
*   **Endpoint Specifications**:
    *   `METHOD /endpoint/path`
        *   **Headers**: `Authorization: Bearer <token>`
        *   **Request Schema**:
            ```json
            {}
            ```
        *   **Response Schema (200 OK / 201 Created)**:
            ```json
            {}
            ```
        *   **Error Responses (400, 401, 403, 404, 409)**: Centralized problem detail structure.

## 3. Database Schema & Persistence Strategy
*   **PostgreSQL Migration (JPA/Hibernate / DDL)**:
    *   Table definitions, column mapping types (e.g. JSONB columns, primary/foreign keys).
    *   Indexing strategy (e.g., GIN indexes for JSONB columns, B-Tree indexes for foreign keys).
*   **MongoDB Document Structure (BSON / Collections)**:
    *   Document structure mapping, sub-document lists (e.g., embedded outboxes, custom maps).
    *   Compound or wildcard index definitions (`WildcardIndexed`).

## 4. Cache & Synchronization Strategy
*   **Caching Layers**: Explain L1 (Caffeine) and L2 (Redis) cache structures, if any.
*   **Key Namespaces & TTL Configurations**: E.g. `@Cacheable(value = "products", key = "#id")`.
*   **Cache Eviction & Stampede Prevention**: How caches are cleared when write actions execute.

## 5. Technical Research & Trade-offs
*   **Option A vs. Option B**: Why this design was chosen.
*   **Blast Radius Analysis**: Identify what could break in upstream or downstream microservices.
*   **Performance & Scale Implications**: Latency analysis, memory bounds, database query execution impact.

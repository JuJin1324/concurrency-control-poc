# GEMINI.md - Project Context & Instructions

## 1. Project Overview

**Name:** Concurrency Control PoC (Proof of Concept)
**Goal:** Verify and compare 4 methods of concurrency control in a high-traffic environment (Stock reduction scenario).
**Primary Tech Stack:**
-   **Language:** Java 21
-   **Framework:** Spring Boot 3.x
-   **Database:** MySQL 8.0 (Persistence), Redis 7.0 (Distributed Lock/Cache)
-   **Testing:** JUnit 5, ArchUnit (Architecture), k6 (Load Testing)
-   **Infrastructure:** Docker Compose

### Core Objective
To quantitatively compare the performance (TPS, Latency) and stability of the following 4 concurrency control mechanisms:
1.  **Pessimistic Lock** (MySQL `SELECT ... FOR UPDATE`)
2.  **Optimistic Lock** (MySQL Versioning)
3.  **Redis Distributed Lock** (Redisson)
4.  **Redis Lua Script** (Atomic Operation)

## 2. Current Status: Sprint 0

**Phase:** Planning & Foundation (Sprint 0)
**Current Date:** 2026-01-22

The project is currently in **Sprint 0**, focusing on Platform Engineering and Architecture Design. No application code (`src/`) exists yet.

### Active Sprint Plan: `.agile/sprints/sprint-0/plan.md`
-   **Iteration 1 (Infrastructure):** Define and implement Docker Compose (MySQL + Redis). **<-- CURRENT FOCUS**
-   **Iteration 2 (Scaffolding):** Create Spring Boot project structure and ArchUnit tests.
-   **Iteration 3 (Visualization):** Complete architecture diagrams (C4, Sequence) and documentation.

## 3. Directory Structure

-   **`.agile/`**: Agile project management files.
    -   `sprints/sprint-0/plan.md`: Detailed plan for the current sprint.
    -   `README.md`: Agile process documentation.
-   **`docs/`**: Project documentation.
    -   `adr/`: Architecture Decision Records (e.g., why 4 methods, why MySQL/Redis).
    -   `architecture/`: Architecture diagrams and descriptions.
    -   `proposals/`: Initial project proposal and scope definition.
-   **`brainstorm.md`**: Initial thought process and history of project definition.
-   **`how-diagram.md`**: (Likely) Drafts for diagrams.

## 4. Development Conventions

-   **Workflow:** "Visualization First" -> Review -> Implementation. Always visualize or plan before coding.
-   **Architecture:** Layered Architecture (Controller -> Service -> Repository -> Domain).
-   **Domain logic:** DDD-lite (Rich Domain Model for Stock).
-   **Testing:**
    -   **Unit:** JUnit 5.
    -   **Architecture:** ArchUnit (Enforce dependency rules).
    -   **Load:** k6 (Scenario-based load testing).
-   **Infrastructure:** All infrastructure MUST be reproducible via `docker-compose` and managed via `Makefile`.

## 5. Next Steps (Immediate)

1.  **Infrastructure Implementation (Iteration 1):**
    -   Create `docker-compose.yml` for MySQL and Redis.
    -   Create `Makefile` for easy management (`make up`, `make down`).
    -   Create data initialization scripts (SQL).
2.  **Project Scaffolding (Iteration 2):**
    -   Initialize Spring Boot project.
    -   Set up package structure (`domain`, `service`, `repository`, `controller`).
    -   Add ArchUnit tests.

**Note to Agent:** Always check `.agile/sprints/sprint-0/plan.md` for the most up-to-date specific tasks and acceptance criteria.

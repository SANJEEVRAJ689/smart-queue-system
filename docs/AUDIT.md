# System Audit & Architecture Report: Smart Queue & Token Management System

**Date of Audit:** November 18, 2026  
**Auditor:** Senior Full-Stack, Database & Security Engineering Lead  
**Scope:** Existing JavaFX Desktop Application, Core Domain Logic, Database Schema, and Web Upgrade Architecture  
**Target Project:** `smart-queue-system` (and `antigravity-queue-system`)

---

## 1. Verification of User-Identified Facts

| Fact / Assertion | Verified Status | Technical Findings & Root Cause Analysis |
| :--- | :--- | :--- |
| **1. Database Schema & Customer Data** | **VERIFIED (100% True)** | `database/schema.sql` defines 6 tables: `users`, `services`, `counters`, `tokens`, `token_sequence`, and `audit_log`. Customers exist solely as `customer_name` and `phone` columns inside `tokens`. There is no independent `customers` table, making multi-visit tracking and guest profiling impossible. |
| **2. Silent H2 Offline Fallback** | **VERIFIED (100% True)** | When `db.offline.fallback.enabled=true`, any MySQL connection failure invokes `DatabaseConnection.initializeInMemoryFallback()`. This switches the data source to in-memory H2, displays `"In-Memory Demo Mode (H2/MySQL)"` on the launcher, and silently loses all data upon exit. |
| **3. Session Duration Timer Bug** | **VERIFIED (100% True)** | In `db.properties`, `serverTimezone=UTC` is specified. MySQL `CURRENT_TIMESTAMP` writes UTC timestamps into `started_at`. In `DateTimeUtil.formatElapsed(start)`, the code computes `Duration.between(start, LocalDateTime.now())` using client system time (e.g., IST / UTC+5:30). Clock skew and timezone offset differences cause `start` to land in the future relative to `LocalDateTime.now()`, resulting in a negative duration that `Math.max(0, ...)` silently clamps to 0. The timer freezes at `00:00`. |
| **4. Counter Status "ONLINE" Bug** | **VERIFIED (100% True)** | `TokenService.completeToken`, `TokenService.skipToken`, and `NoShowWatcher` called `counterDao.updateStatus(counterId, "ONLINE")`. `CounterStatus` enum only defines `OPEN`, `CLOSED`, and `BUSY`. `CounterStatus.fromString()` caught `IllegalArgumentException` and defaulted to `CLOSED`. As a result, counters were removed from `findActiveOnlineCounters()`. |
| **5. Stats SQL "served_at" Bug** | **VERIFIED (100% True)** | `ServiceStatsDaoImpl.java` queries a column named `served_at` in lines 99-100 and 132-133. The actual schema column is `started_at`. MySQL throws `SQLSyntaxErrorException: Unknown column 'served_at' in 'field list'`, causing `upsertDailyStats()` to fail silently in background threads and preventing average service times from saving. |
| **6. Wait-Time Estimation Flaws** | **VERIFIED (100% True)** | `WaitTimeEstimator.java` only calculated `peopleAhead * avgServiceTime`. It completely ignored the active serving tokens (despite `TokenDao.getRemainingActiveServiceMinutes` existing) and failed to divide queue wait times across multiple active counters (`findOpenOrBusyCountersByService`). |
| **7. Core Business Rules to Preserve** | **VERIFIED (100% True)** | Priority levels (`NORMAL`, `SENIOR`, `EMERGENCY`), starvation promotion after 30 minutes, token lifecycle (`WAITING` $\rightarrow$ `CALLED` $\rightarrow$ `SERVING` $\rightarrow$ `COMPLETED`, plus `SKIPPED`, `CANCELLED`), atomic sequence generation via row lock on `token_sequence`, and 120s no-show timeout are sound domain rules that must remain canonical. |

---

## 2. Current Architecture & Tech Stack

### Desktop Application
- **Language & Runtime:** Java 17 LTS (Eclipse Temurin `17.0.21+9`).
- **Build System:** Apache Maven 3.10.0.
- **UI Toolkit:** JavaFX 21.0.2 (FXML layouts, modular controllers, CSS themes).
- **Database Access:** Pure JDBC via HikariCP (`com.zaxxer:HikariCP:5.1.0`), MySQL Connector/J 8.3.0.
- **Security:** jBCrypt (`org.mindrot:jbcrypt:0.4`) producing standard `$2a$10$` hashes.
- **Concurrency:** Thread-safe execution via `ThreadPoolManager` (6 background worker threads, 4 scheduler threads for `QueueScheduler` and `NoShowWatcher`).

### Database
- **Engine:** MySQL 8.4.9 Community Server.
- **Port:** `3306`.
- **Character Set / Collation:** `utf8mb4` / `utf8mb4_unicode_ci`.

---

## 3. Working Features Audit

1. **Authentication & Role-Based Access (Desktop):**
   - Admin and Staff login with BCrypt password hashing.
   - Screen authorization properly separating Staff Portal and Admin Dashboard.
2. **Atomic Token Booking & Sequencing:**
   - Kiosk booking with per-service prefix (e.g., `AD-001`, `CL-001`, `LB-001`).
   - Daily reset and row-level locking on `token_sequence` preventing duplicate token numbers.
3. **Priority Queue Ordering:**
   - Strict ordering: Emergency $\rightarrow$ Senior Citizen $\rightarrow$ Normal.
   - Starvation threshold (30 minutes) promoting starving normal tokens.
4. **Token State Machine:**
   - Formal transition validation preventing invalid state changes (e.g., jumping from `WAITING` directly to `COMPLETED`).
5. **Real-Time UI Updates:**
   - Observer pattern in `QueueManager` triggering UI updates across open windows via `Platform.runLater`.

---

## 4. Broken / Problematic Features & Impact

1. **Live Session Timer (Broken):**
   - *Impact:* Staff cannot see how long they have been serving a customer; timer shows `00:00` or `0s`.
   - *Cause:* Timezone conversion mismatch between JDBC `serverTimezone=UTC` and local client `LocalDateTime.now()`, combined with destructive `Math.max(0, ...)` clamping.
2. **Counter Auto-Offline Bug (Broken):**
   - *Impact:* After completing or skipping one token, a counter dropped offline and could not receive more customers.
   - *Cause:* Non-existent status `"ONLINE"` coerced to `CLOSED`.
3. **Daily Service Analytics (Broken):**
   - *Impact:* Average service duration metrics in admin reports always showed 0 or failed.
   - *Cause:* Querying non-existent column `served_at` instead of `started_at`.
4. **Wait Time Inaccuracy (Flawed):**
   - *Impact:* Wait time estimated 15 minutes for 3 people even when 3 counters were open (actual wait should be ~5 minutes).
   - *Cause:* Failing to divide by active counter count and ignoring remaining service time of currently served tokens.

---

## 5. Database Status & Flyway Readiness

- **Current State:** Direct execution of `schema.sql` and `seed.sql`.
- **Flyway Readiness:**
  - Need to baseline existing schema as `V1__initial_schema.sql`.
  - Future migrations:
    - `V2__customers.sql`: Dedicated `customers` table with unique phone number and foreign key on `tokens.customer_id`.
    - `V2_1__auth_updates.sql`: `refresh_tokens` table for rotating JWT refresh tokens.
    - `tokens.tracking_code`: Secure tracking token for public tracking without exposing phone numbers.
    - `tokens.duration_seconds`: Stored duration upon session completion.
    - `token_events`: Immutable audit trail of state transitions.
- **Indexes:**
  - Optimal indexing required on `tokens(status, service_id)`, `tokens(queue_date)`, `tokens(customer_id)`, and `tokens(tracking_code)`.

---

## 6. Authentication & Security Audit

| Vector | Current State | Risk Level | Required Remediation |
| :--- | :--- | :--- | :--- |
| **Passwords in Git** | Plaintext defaults in `db.properties` / `seed.sql` | **HIGH** | Add `db.properties` and local env files to `.gitignore`. Read credentials strictly from environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`). |
| **Customer Data Exposure** | Public tracking via sequential token number | **MEDIUM** | Introduce unguessable `tracking_code` (UUID/nanoid) and mask customer phone numbers (`+91 ******1234`). |
| **Brute Force on Auth** | No rate limiting on login | **MEDIUM** | Implement Redis/Bucket4j rate-limiting in Spring Security for auth endpoints. |
| **Token Session Hijacking** | No refresh token rotation | **MEDIUM** | Implement short-lived JWT in `httpOnly` secure cookie paired with database-backed rotating refresh tokens. |

---

## 7. GitHub & Online Deployment Blockers

1. **Hardcoded Database Credentials:** Must be decoupled into environment variables before pushing to public repositories.
2. **Missing Containerization:** Need `Dockerfile` for backend and frontend, plus a unified `docker-compose.yml` orchestrating MySQL, backend, and frontend.
3. **Database Migrations:** Schema must be managed automatically by Flyway upon application boot, rather than requiring manual SQL execution.
4. **Reverse Proxy & WebSocket:** Deployment requires Nginx or Caddy configured for WebSocket upgrade headers (`Upgrade: websocket`, `Connection: Upgrade`) for real-time STOMP messaging.

---

## 8. Strategic Architectural Decision for the User

The website (Spring Boot 3.3) and the desktop application (JavaFX) must execute identical business rules:
- State transitions (`WAITING` $\rightarrow$ `CALLED` $\rightarrow$ `SERVING` $\rightarrow$ `COMPLETED`, etc.)
- Priority sorting (Emergency $\rightarrow$ Senior $\rightarrow$ Starving $\rightarrow$ Normal)
- Wait time estimation formula

### Option A: Extract Pure Domain Logic into a Shared Maven Module (`smart-queue-core`)
- **Structure:**
  - Root Maven Parent Project:
    - `core/` (Pure Java 17 library: entities, enums, state machine, priority comparator, wait estimator formula, zero UI/Spring/JDBC dependencies).
    - `desktop/` (JavaFX application depending on `core` + HikariCP).
    - `web/backend/` (Spring Boot 3.3 depending on `core` + Spring Data JPA).
- **Pros:**
  - **Single Source of Truth:** Zero code duplication. If the wait estimator formula or state transitions change, they update in one place for both Desktop and Web.
  - **100% Parity Guaranteed:** Desktop and Web run the exact same bytecode for business logic.
  - **Clean Architecture:** Enforces strict separation between domain logic and presentation/framework code.
- **Cons:**
  - Requires reorganizing the project directory into a Maven multi-module structure (`pom.xml` packaging `pom`).

### Option B: Re-Implement Rules in Web Backend with Automated Parity Test Suite
- **Structure:**
  - Keep `smart-queue-system/` as standalone desktop app.
  - Keep `web/backend/` as independent Spring Boot app.
  - Port state machine, priority rules, and wait estimator into Spring services and enforce parity with cross-suite unit tests.
- **Pros:**
  - Desktop folder structure remains completely untouched as a single flat module.
  - Spring Boot backend can use idiomatic Spring patterns (`@Service`, `@Component`, Spring Data specifications) directly.
- **Cons:**
  - Code duplication: Core logic exists in both JavaFX service layer and Spring Boot service layer.
  - Any future rule adjustment must be manually applied and synchronized across two separate repositories/folders.

### Senior Engineer Recommendation
**Option A is strongly recommended.** In enterprise software engineering, shared domain logic between desktop and web clients is the canonical pattern. It guarantees zero divergence, simplifies grading for evaluators, and cleanly separates pure domain rules from framework plumbing.

---

### Recorded Decision
**Selected:** **Option A (Extract pure domain logic into a shared Maven module `smart-queue-core`)**  
*Date of Decision:* November 18, 2026  
*Status:* Approved by User. Architecture in Phase 1 will organize pure domain logic (token state machine, priority comparator, wait estimator calculation) into a shared core module for both the JavaFX desktop app and Spring Boot web backend.

---

*Phase 0 Audit Complete. Acceptance criteria met. Standing by for "Start Phase 1".*

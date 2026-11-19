# Smart Queue & Token Management System — Code Walkthrough Guide

This document maps every architectural concept, software pattern, and functional feature in the system to the exact class, package, and method where it is implemented.

---

## 🗺️ Architectural Mapping Table

| Architectural Concept | Primary Java Class / File | Key Methods / Implementation Details |
|-----------------------|---------------------------|--------------------------------------|
| **Application Bootstrap & Lifecycle** | `SmartQueueApp.java` | `start(Stage)` loads main launcher; `stop()` executes graceful shutdown of all thread pools and HikariCP connection pool. |
| **Pessimistic Row-Level Locking** | `com.smartqueue.dao.impl.TokenDaoImpl` | `claimNextToken(Connection, int, int, int)`: executes `SELECT ... FOR UPDATE` with priority `ORDER BY` to lock and claim next token atomically. |
| **Race-Safe Calling** | `com.smartqueue.concurrent.AutoCaller` | `callNext(int, int)`: begins database transaction (`setAutoCommit(false)`), claims token via row lock, updates counter to `BUSY`, records audit log, and commits. |
| **Token State Machine Enforcement** | `com.smartqueue.service.TokenService` | `validateTransition(TokenStatus, TokenStatus)`: strictly allows legal state changes (`WAITING` $\rightarrow$ `CALLED` $\rightarrow$ `SERVING` $\rightarrow$ `COMPLETED`, etc.) and throws `AppException` on invalid jumps. |
| **Dynamic Wait Time Estimation** | `com.smartqueue.service.WaitTimeEstimator` | `estimateForToken()`, `estimateForNewBooking()`: calculates average service time over last N completed tokens, divides by open counters, adds remaining duration of active tokens. |
| **Starvation Prevention** | `com.smartqueue.model.Token` | `getEffectiveRank(int threshold)`: promotes `NORMAL` priority tokens waiting $\ge 30$ minutes to `SENIOR` rank (Rank 1). |
| **Atomic Token Sequence Generation** | `com.smartqueue.dao.impl.TokenSequenceDaoImpl` | `getNextSequenceNumber()`: executes `SELECT last_number ... FOR UPDATE` on `token_sequence` table, increments and commits atomically. |
| **Background Thread Pool & Schedulers** | `com.smartqueue.concurrent.ThreadPoolManager` | `submitWorkerTask(Runnable)` for offloading DB work; `scheduleAtFixedRate()` for periodic background jobs; `shutdown()` for clean termination. |
| **Periodic Auto-Calling Orchestration** | `com.smartqueue.concurrent.QueueScheduler` | `run()`: executes every 3 seconds; triggers `NoShowWatcher` and auto-calls waiting tokens for idle `OPEN` counters. |
| **Automated No-Show Timeout Enforcement** | `com.smartqueue.concurrent.NoShowWatcher` | `run()`: queries tokens in `CALLED` state exceeding 120s timeout, transitions them to `SKIPPED`, and frees assigned counters. |
| **Live Display Board Polling** | `com.smartqueue.concurrent.DisplayRefresher` | `run()`: queries active serving counters and next 5 tokens every 2 seconds, pushes updates to UI via `Platform.runLater()`. |
| **Password Security & Hashing** | `com.smartqueue.util.PasswordUtil` | `hashPassword(String)`: generates BCrypt hash with 10 salt rounds; `checkPassword()` verifies entered password against stored hash. |
| **Database Connection & Fallback Pool** | `com.smartqueue.util.DatabaseConnection` | `getConnection()`: manages HikariCP pool to MySQL; auto-detects MySQL outage and initializes embedded in-memory H2 database with schema and seed. |
| **Non-Blocking UI Dispatching** | `com.smartqueue.service.QueueManager` | `triggerQueueUpdate()`: uses `Platform.runLater()` to safely notify all registered JavaFX controllers from background worker threads. |
| **Hardware Sound & Voice Announcements** | `com.smartqueue.util.SoundUtil` | `playChimeAsync()`: plays two-tone bell chime via `javax.sound.sampled`; `speakAnnouncementAsync()`: executes Windows PowerShell TTS synthesizer with silent fallback. |
| **Input Validation** | `com.smartqueue.util.Validator` | `isValidPhone()`, `isValidName()`, `isValidUsername()`, `isValidPrefix()`: validates regex formats before sending queries to database. |
| **CSV Analytical Reporting** | `com.smartqueue.service.ReportService` | `exportTokensToCsv(File, LocalDate)`: formats token records, wait durations, and service times into standard comma-separated text. |

---

## 📂 Package-by-Package Directory Walkthrough

### 1. `com.smartqueue.model`
Contains POJO domain classes representing normalized relational entities:
- `Token`: represents a customer's token pass, with priority, status, timestamps, and `Comparable` implementation.
- `Priority`: enum (`EMERGENCY = 0`, `SENIOR = 1`, `NORMAL = 2`).
- `TokenStatus`: enum (`WAITING`, `CALLED`, `SERVING`, `COMPLETED`, `SKIPPED`, `CANCELLED`).
- `CounterStatus`: enum (`OPEN`, `CLOSED`, `BUSY`).
- `Counter`: service desk representation with counter number and assigned staff member.
- `ServiceEntity`: department configuration with name, prefix (e.g. `AD`), and default avg service duration.
- `User`: staff and administrator user entity with BCrypt password hash and `Role`.
- `AuditLog`: historical event log entry.

### 2. `com.smartqueue.dao` & `com.smartqueue.dao.impl`
Interface-driven JDBC persistence layer:
- `TokenDao` / `TokenDaoImpl`: All CRUD operations for tokens, queue lookups, and `claimNextToken()` row locking.
- `CounterDao` / `CounterDaoImpl`: Queries for physical desks and status changes.
- `ServiceDao` / `ServiceDaoImpl`: Department metadata management.
- `TokenSequenceDao` / `TokenSequenceDaoImpl`: Atomic sequence counter per service and date.
- `UserDao` / `UserDaoImpl`: User credentials and staff management.
- `AuditLogDao` / `AuditLogDaoImpl`: Audit trail recording.

### 3. `com.smartqueue.service`
Pure business logic:
- `TokenService`: Orchestrates token booking, state transitions, validation, and audio announcements.
- `WaitTimeEstimator`: Pure mathematical calculations for wait estimates.
- `AuthService`: Session management and BCrypt authentication.
- `ReportService`: KPI calculation, summary metrics, and CSV file generation.
- `QueueManager`: Observer pattern coordinator for UI listeners.

### 4. `com.smartqueue.concurrent`
Multithreading and asynchronous execution:
- `ThreadPoolManager`: Worker thread pool and scheduled thread pool management.
- `AutoCaller`: Atomic token assignment to counters.
- `QueueScheduler`: 3-second cycle for auto-calling and queue maintenance.
- `NoShowWatcher`: Timeout cleaner for abandoned calls.
- `DisplayRefresher`: 2-second background pusher for display boards.

### 5. `com.smartqueue.controller`
JavaFX View Controllers:
- `MainLauncherController`: Hub window allowing testing of any portal.
- `LoginController`: Modal authentication with quick viva demo credential buttons.
- `KioskController`: Self-service booking kiosk, live wait time calculation, and status checking.
- `StaffDashboardController`: Desk operations (Call, Serve, Complete, Skip, Recall, Transfer).
- `DisplayBoardController`: Digital waiting-hall TV display with large cards.
- `AdminDashboardController`: Management tabs, BarCharts, CSV export, and audit log viewer.

### 6. `src/main/resources`
- `fxml/`: Clean, modular FXML layouts for each screen.
- `css/styles.css`: Complete modern stylesheet with high-contrast signage for the display board.
- `db.properties` / `app.properties`: Externalized configuration.
- `database/schema.sql` / `database/seed.sql`: MySQL production database scripts.
- `database/schema_h2.sql` / `database/seed_h2.sql`: Embedded H2 fallback scripts.

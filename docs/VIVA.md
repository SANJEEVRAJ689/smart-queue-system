# Smart Queue & Token Management System — Viva & Oral Exam Guide

This document prepares a 2nd-year CSE student for viva questions, technical evaluations, and lab presentations. Each question contains a concise, technically sound answer and the rationale behind the design.

---

### Q1: What is the overall architecture of this project?
**Answer:**  
The project follows a **Layered MVC (Model-View-Controller)** pattern:
- **View:** JavaFX FXML files and CSS (`resources/fxml/`, `resources/css/styles.css`).
- **Controller:** JavaFX Controllers (`com.smartqueue.controller.*`) that handle UI events, format data, and dispatch background tasks.
- **Service:** Business logic layer (`com.smartqueue.service.*`) that owns transaction boundaries, enforces the token state machine, and calculates wait time estimates.
- **DAO (Data Access Object):** Interface-driven JDBC layer (`com.smartqueue.dao.*`) executing `PreparedStatements` against MySQL/H2.
- **Concurrent Layer:** `com.smartqueue.concurrent.*` managing background threads, periodic auto-calling, and display refreshes.
- **Model:** Domain entities and enums (`Token`, `User`, `Counter`, `Priority`, `TokenStatus`).

---

### Q2: Why did you not use Spring Boot or other heavy frameworks?
**Answer:**  
1. **Curriculum Alignment:** Demonstrates core Java competencies: raw JDBC, HikariCP connection pooling, multi-threading with `ExecutorService`, and JavaFX desktop UI without abstraction magic.
2. **Deterministic Startup:** The desktop application boots in milliseconds with zero framework overhead.
3. **Transparency:** Every thread, transaction, SQL statement, and lifecycle hook is written explicitly, making it easy to defend in a viva.

---

### Q3: How do you prevent the JavaFX UI from freezing during database queries?
**Answer:**  
JavaFX has a single **JavaFX Application Thread** responsible for rendering graphics and processing user input. If you execute a blocking SQL query on this thread, the UI stops responding.
- **Solution:** All database operations are submitted to a `FixedThreadPool` via `ThreadPoolManager.getInstance().submitWorkerTask(Runnable)`.
- When the query completes, the results are dispatched back to the UI thread using `Platform.runLater(Runnable)`, ensuring thread safety.

---

### Q4: How do you prevent race conditions when two service counters call for a token at the exact same millisecond?
**Answer:**  
We prevent race conditions at the database level using an atomic transaction with **pessimistic row-level locking**:
1. We begin a transaction with `conn.setAutoCommit(false)`.
2. We query the next waiting token using:
   ```sql
   SELECT id FROM tokens 
   WHERE service_id = ? AND status = 'WAITING' 
   ORDER BY ... LIMIT 1 FOR UPDATE
   ```
3. `FOR UPDATE` places an exclusive lock on that specific row. Any other concurrent transaction trying to read or lock the same row is blocked until the first transaction commits.
4. We immediately execute `UPDATE tokens SET status = 'CALLED', counter_id = ? WHERE id = ?`.
5. Finally, we execute `conn.commit()`.  
This guarantees that **no two counters can ever claim the same token**.

---

### Q5: What is the Token State Machine and what transitions are allowed?
**Answer:**  
The token lifecycle is strictly enforced in `TokenService.validateTransition()`:
- `WAITING` $\rightarrow$ `CALLED` (Staff calls next or AutoCaller triggers)
- `WAITING` $\rightarrow$ `CANCELLED` (Customer or Admin cancels)
- `CALLED` $\rightarrow$ `SERVING` (Customer approaches desk and staff starts work)
- `CALLED` $\rightarrow$ `SKIPPED` (Customer fails to appear after timeout)
- `SERVING` $\rightarrow$ `COMPLETED` (Staff marks service finished)
- `SKIPPED` $\rightarrow$ `WAITING` (Staff recalls customer; token goes to the back of the queue)

Any other transition (e.g., `WAITING` $\rightarrow$ `COMPLETED`) throws an `AppException` and is rejected.

---

### Q6: How does priority queue ordering work, and how do you prevent starvation?
**Answer:**  
The queue rank is ordered as:
1. `EMERGENCY` (Rank 0) — Medical emergency, severe disability
2. `SENIOR` (Rank 1) — Senior citizen (60+), pregnant women
3. `NORMAL` (Rank 2) — Regular queue token

**Starvation Prevention Rule:**  
If a high volume of priority customers arrive, normal customers could theoretically wait indefinitely ("starvation"). To solve this:
- If a `NORMAL` token has been waiting $\ge 30$ minutes (configurable in `app.properties`), its effective rank is automatically elevated to `Rank 1` (equivalent to `SENIOR`).
- Within the same rank, strict **FIFO (First-In, First-Out)** by `created_at` timestamp applies.

---

### Q7: How is the estimated waiting time calculated?
**Answer:**  
Estimated wait time is calculated dynamically in `WaitTimeEstimator`:
$$\text{Estimated Wait} = \left\lceil \frac{\text{Tokens Ahead}}{\text{Open Counters}} \times \text{Avg Service Minutes} \right\rceil + \text{Remaining Time of Active Tokens}$$
- `Avg Service Minutes`: Average duration of the last 20 completed tokens for that service. If fewer than 3 tokens exist in history, it defaults to `services.default_avg_minutes`.
- `Open Counters`: The count of currently active counters for that service (minimum 1).
- `Tokens Ahead`: Count of waiting tokens with higher priority rank or earlier creation time.

---

### Q8: How does the application generate unique token numbers without duplicates?
**Answer:**  
Token sequence numbers (e.g., `AD-001`, `AD-002`) are managed in a dedicated `token_sequence` table with composite primary key `(service_id, queue_date)`.
- When generating a token number, a transaction opens with `SELECT last_number FROM token_sequence WHERE service_id = ? AND queue_date = ? FOR UPDATE`.
- The sequence is incremented by 1, saved, and committed atomically.
- This prevents duplicate token numbers even if dozens of kiosks book tokens simultaneously.

---

### Q9: Why is HikariCP used instead of creating a new `DriverManager.getConnection()` every time?
**Answer:**  
Creating a new physical TCP connection to MySQL involves network handshakes, authentication, and resource allocation, taking 50–200ms per query.
- **HikariCP** maintains a pool of pre-warmed, reusable connections.
- Borrowing a connection from the pool takes microseconds (< 0.1ms).
- It also limits maximum connections (`db.pool.max=10`), protecting MySQL from being overwhelmed under high load.

---

### Q10: How are user passwords stored securely?
**Answer:**  
Passwords are **never** stored in plain text. We use **jBCrypt** with salt rounds:
- When a user is created: `String hash = BCrypt.hashpw(plainText, BCrypt.gensalt(10));`
- When verifying login: `boolean matches = BCrypt.checkpw(enteredPassword, storedHash);`
- BCrypt incorporates a random 128-bit salt and an adaptive key derivation function (Eksblowfish), making rainbow table attacks and brute force computationally infeasible.

---

### Q11: How do you prevent SQL Injection?
**Answer:**  
Every single database query uses **`PreparedStatement` with parameterized placeholders (`?`)**:
```java
String sql = "SELECT * FROM users WHERE username = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, username);
    ...
}
```
The MySQL driver transmits the SQL statement structure and the parameter values separately to the database engine. User input is treated strictly as literal data, completely neutralizing SQL injection.

---

### Q12: How does the No-Show Watcher work?
**Answer:**  
`NoShowWatcher` is a scheduled background task running every few seconds:
1. It queries tokens in `CALLED` status where `TIMESTAMPDIFF(SECOND, called_at, NOW()) > timeout_seconds` (default: 120 seconds).
2. It transitions these overdue tokens to `SKIPPED`.
3. It frees the associated counter (`counterDao.updateStatus(counterId, "ONLINE")`).
4. It logs an audit event and triggers `QueueManager.triggerQueueUpdate()` so the counter can auto-call the next waiting customer.

---

### Q13: How does the Live Display Board update without user interaction?
**Answer:**  
The Display Board uses a hybrid approach:
1. **Periodic Refresh:** `DisplayRefresher` runs every 2 seconds via a background `ScheduledExecutorService`, querying currently serving counters and the next 5 tokens.
2. **Event-driven Push:** When any counter changes state (token called, served, skipped), `QueueManager.getInstance().triggerQueueUpdate()` pushes an immediate update via `Platform.runLater()`.
3. An audio chime (`javax.sound.sampled`) and speech synthesizer notify visitors in the waiting hall.

---

### Q14: How does the application handle offline mode or database unavailability?
**Answer:**  
In `DatabaseConnection.java`, when initializing HikariCP:
- The system attempts to connect to the primary MySQL database.
- If MySQL is offline (e.g., student laptop without MySQL installed), it catches the connection error and seamlessly initializes an **embedded in-memory H2 database** in MySQL compatibility mode (`MODE=MySQL`).
- It executes `schema_h2.sql` and `seed_h2.sql` automatically, allowing complete zero-config demo presentations!

---

### Q15: How are threads cleaned up when the application exits?
**Answer:**  
In `SmartQueueApp.stop()`:
1. `QueueManager.getInstance().shutdown()` shuts down the background scheduler and worker pools with `awaitTermination()`.
2. `ThreadPoolManager.getInstance().shutdown()` shuts down worker threads.
3. `DatabaseConnection.shutdown()` closes the HikariCP connection pool, closing all open socket connections cleanly.
This ensures zero zombie threads remain running in the OS.

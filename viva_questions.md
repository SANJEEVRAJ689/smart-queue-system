# Smart Queue and Token Management System
## College Project Viva Questions and Answers

This document prepares you for questions commonly asked by professors and external examiners during project defense.

---

### Q1. Why can't we execute database queries directly on the JavaFX thread? How is this handled in your project?
**Answer:**  
The JavaFX UI runs on a single event-dispatch thread called the **JavaFX Application Thread**. If a slow network operation, database query, or audio playback is executed directly on this thread, the UI freezes, becoming unresponsive ("Not Responding" in Windows) and dropping animation frames.  
In our project:
- All database operations are offloaded to an `ExecutorService` background worker thread pool (`QueueManager.submitWorkerTask`).
- Once the background worker completes its work and fetches data, it updates the UI controls strictly using `Platform.runLater(() -> { ... })`, which safely posts the update task onto the JavaFX Application Thread event queue.

---

### Q2. What happens if two counter staff click "Call Next" at the exact same millisecond? How do you prevent a race condition?
**Answer:**  
If two counters query the database simultaneously without locking, both could read the exact same waiting token and both attempt to serve the same customer—a classic **race condition**.  
We resolve this at the database transaction level using **pessimistic row locking**:
```sql
SELECT id FROM tokens
WHERE service_id = ? AND status = 'WAITING'
ORDER BY is_priority DESC, created_at ASC
LIMIT 1
FOR UPDATE;
```
When Counter A runs this transaction, MySQL places an exclusive row-level lock on that token. Counter B's transaction must wait. Counter A then updates the token status to `CALLED`, assigns its `counter_id`, and commits. When Counter B acquires the lock, the condition `status = 'WAITING'` is no longer true for that row, so Counter B automatically claims the *next* customer in line. Double-calling is mathematically impossible.

---

### Q3. Explain your Dynamic Wait Time Estimation algorithm. Why is a fallback required?
**Answer:**  
The wait time is calculated dynamically as:
$$\text{Estimated Wait (minutes)} = \text{People Ahead in Service} \times \text{Avg Service Duration (last } N=10 \text{ completed tokens)}$$
- The duration of each completed token is measured as $(\text{completed\_at} - \text{served\_at})$.
- **Fallback Necessity:** Early in the morning or when a service desk has just opened, zero or very few completed tokens exist. Calculating an average from zero data would result in division by zero or an inaccurate estimate (0 minutes). Therefore, if fewer than 3 completed tokens exist for that service today, the algorithm falls back to the configured baseline service duration (e.g. 5 minutes).

---

### Q4. How does the priority queue work? How does a senior citizen jump ahead of an earlier customer?
**Answer:**  
Our queue combines **priority weighting** with **First-Come, First-Served (FIFO)**:
1. Primary sort key: `is_priority DESC` (Priority tokens have weight 1; regular tokens have weight 0).
2. Secondary sort key: `created_at ASC` (Earlier timestamp comes first).  

In the database, the query uses:
```sql
ORDER BY is_priority DESC, created_at ASC
```
In Java memory, the `Token` class implements `Comparable<Token>`:
```java
if (this.priority != other.priority) {
    return this.priority ? -1 : 1; // High priority comes first
}
return this.createdAt.compareTo(other.createdAt); // FIFO for same priority
```
Even if a regular token was booked 15 minutes ago, a senior citizen booking 2 minutes ago has `is_priority = true` and is positioned before the regular token.

---

### Q5. Why is BCrypt used for password storage instead of MD5 or SHA-256?
**Answer:**  
MD5 and standard SHA-256 are fast cryptographic hash functions designed for data integrity, not password storage. Modern GPUs can calculate billions of SHA-256 hashes per second, making them vulnerable to brute-force dictionary and rainbow table attacks.  
**BCrypt** is specifically designed for password security:
1. It uses an **adaptive work factor (cost parameter)** that makes hashing computationally expensive and slow to brute force.
2. It automatically incorporates a **random cryptographic salt** into the 60-character output hash (e.g. `$2a$10$...`), protecting against rainbow table lookup attacks.

---

### Q6. What is HikariCP and why is a connection pool needed in a desktop application?
**Answer:**  
Creating a raw JDBC connection via `DriverManager.getConnection()` is an expensive physical network handshake: TCP handshake, authentication, session initialization, and socket creation taking 50–200 milliseconds per query.  
**HikariCP** is a lightweight, zero-overhead JDBC connection pool:
- It maintains a pool of pre-warmed, active database connections.
- When a worker thread needs to execute a query, it borrows an existing connection in microseconds and returns it to the pool in a `try-with-resources` block.
- It prevents opening too many simultaneous socket connections to MySQL.

---

### Q7. Explain the Model-View-Controller (MVC) separation in your codebase.
**Answer:**  
- **Model (`com.smartqueue.model`):** Plain Java objects representing business data (`Token`, `User`, `ServiceEntity`, `Counter`, `TokenStatus`, `PriorityLevel`) with no UI or SQL code.
- **View (`src/main/resources/fxml`, `styles.css`):** Declarative XML files defining the visual component hierarchy, bound to controllers via `fx:id` and action handlers.
- **Controller (`com.smartqueue.controller`):** Java classes that handle UI events (button clicks, table selections) and update views.
- **DAO & Service Layers (`dao`, `service`):** Controllers do not write raw SQL; they delegate to service classes (`TokenService`, `AuthService`, `QueueManager`), which interact with `DAO` implementations. This ensures loose coupling and high testability.

---

### Q8. How do token numbers (e.g. `A-001`, `F-005`) reset daily without collisions?
**Answer:**  
When `TokenDao.generateNextTokenNumber(prefix)` is called:
1. It queries the maximum token issued **today** for that department prefix:
   ```sql
   SELECT token_number FROM tokens
   WHERE token_number LIKE ? AND CAST(created_at AS DATE) = CURRENT_DATE()
   ORDER BY id DESC LIMIT 1
   ```
2. If no tokens exist for today, it starts at `001` (e.g. `A-001`).
3. If `A-042` exists, it extracts `42`, increments to `43`, and formats as `A-043`.
4. At midnight, `CURRENT_DATE()` changes, so the query automatically starts again from `001`.

---

### Q9. How does the Live Display Board refresh every 2 seconds without lagging?
**Answer:**  
Instead of creating a new `Thread` every 2 seconds, we use a single-threaded `ScheduledExecutorService` (`DisplayBoard-Poller`):
```java
displayScheduler.scheduleAtFixedRate(this::refreshDisplayData, 2, 2, TimeUnit.SECONDS);
```
- The scheduled worker executes the database queries asynchronously on its background daemon thread.
- Only when the fresh token data is retrieved does it call `Platform.runLater()` to swap the labels and counter cards in JavaFX.
- In addition, an event listener in `QueueManager` pushes instant updates when a staff member calls or completes a token, providing immediate feedback without waiting for the next 2-second tick.

---

### Q10. What happens when the user clicks the window close button? How is graceful shutdown achieved?
**Answer:**  
In `SmartQueueApp.java`, we override the JavaFX lifecycle method `stop()`:
```java
@Override
public void stop() {
    QueueManager.getInstance().shutdown();
    DatabaseConnection.shutdown();
}
```
1. It calls `scheduler.shutdown()` and `workerPool.shutdown()`.
2. It calls `awaitTermination(2, TimeUnit.SECONDS)` to allow any running database queries to finish committing safely.
3. If tasks take too long, it calls `shutdownNow()` to cancel remaining threads.
4. It calls `dataSource.close()` on HikariCP to cleanly close all database socket connections.  
This guarantees zero orphaned background threads and prevents database connection leaks.

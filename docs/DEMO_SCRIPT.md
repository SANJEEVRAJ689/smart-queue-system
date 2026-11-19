# Smart Queue & Token Management System — 5-Minute Live Demo Script

This script provides an exact, step-by-step presentation flow for college project evaluations, lab exams, and viva demonstrations.

---

## ⏱️ Demo Timeline Overview (5 Minutes)

| Time | Phase | Focus Screen | Key Highlight to Announce |
|------|-------|--------------|---------------------------|
| **0:00 - 0:45** | **Architecture & Bootstrap** | Main System Launcher | MVC, HikariCP, BCrypt, Zero-Config H2 / MySQL |
| **0:45 - 2:00** | **Customer Kiosk & Priority Queue** | Booking Kiosk (`kiosk.fxml`) | Live wait estimation, dynamic position, emergency priority override |
| **2:00 - 3:15** | **Staff Operations & State Machine** | Staff Dashboard (`staff_dashboard.fxml`) | Call Next, Start Serving, Complete, Skip, Recall, Transfer |
| **3:15 - 4:15** | **Waiting Hall Digital Signage** | Live Display Board (`display_board.fxml`) | Electronic cards, next 5 waiting, audio chime & speech announcement |
| **4:15 - 5:00** | **Analytics & Audit Reporting** | Admin Dashboard (`admin_dashboard.fxml`) | Real-time KPI cards, peak hours chart, CSV export, audit trail |

---

## 🎬 Step-by-Step Walkthrough

### Step 1: Launch the System (0:00 - 0:45)
1. Run from the terminal:
   ```bash
   mvn clean javafx:run
   ```
2. The **Main System Launcher** opens.
3. **What to say to evaluators:**
   > *"Good morning professors. This is our desktop Smart Queue and Token Management System built with Java 17, JavaFX 21, HikariCP, and BCrypt security. As you can see on the top right, it automatically connects to our database pool. If an external MySQL instance is unavailable, our embedded in-memory fallback automatically engages so the application never crashes during a demonstration."*

---

### Step 2: Book 3 Tokens at the Customer Kiosk (0:45 - 2:00)
1. Click **"Open Customer Kiosk"** on the launcher.
2. **Book Token 1 (Normal Regular Customer):**
   - Department: **Admissions** (`AD`)
   - Name: `Alice Johnson`
   - Phone: `9876543210`
   - Priority: Unchecked (Regular)
   - Click **"Generate Digital Token & Ticket"**
   - Result: Generated Token **AD-001**, Position: 1, Estimated Wait: ~5 mins.
   - Click **"💾 Print / Save Slip (TXT)"** to demonstrate physical slip export.
   - Click **"Done / Book Another"**.

3. **Book Token 2 (Normal Regular Customer):**
   - Department: **Admissions** (`AD`)
   - Name: `Bob Smith`
   - Phone: `9876543211`
   - Priority: Unchecked
   - Result: Generated Token **AD-002**, Position: 2.

4. **Book Token 3 (EMERGENCY Priority Customer — The Key Demonstration!):**
   - Department: **Admissions** (`AD`)
   - Name: `Charlie Davis`
   - Phone: `9876543212`
   - Priority: **Checked** $\rightarrow$ Category: **"Medical Emergency / Physical Disability"**
   - Click **"Generate Digital Token & Ticket"**
   - Notice that the preview dynamic wait drops to **0 mins** because of priority rank elevation!
   - Result: Generated Token **AD-003**, Priority: `EMERGENCY`.

5. **Demonstrate Status Check:**
   - Click the **"🔍 Check Token Status"** tab.
   - Enter `AD-003` and `9876543212`.
   - Click **"Check My Status"**.
   - Show live card: `Status: WAITING`, Position: Top priority.

6. **What to say to evaluators:**
   > *"Notice that Charlie booked last, but because he has an EMERGENCY priority tag, our sorting algorithm assigns him Priority Rank 0. When the counter staff calls next, Charlie will be served before Alice and Bob, perfectly matching hospital or college emergency desk policies."*

---

### Step 3: Open the Live Display Board (2:00 - 2:45)
1. In the Launcher, click **"Open Live Display Board"**.
2. Position this window on the right side of the screen.
3. Highlight:
   - Digital real-time clock.
   - Initial counter cards (Counter 1: `ONLINE / IDLE`).
   - "Next in Line" table showing `AD-003` (Emergency) at the very top of upcoming tokens!
4. Click **"🔔 Audio Chime"** to demonstrate hardware sound playback.

---

### Step 4: Counter Staff Portal Operations (2:45 - 4:15)
1. In the Launcher, click **"Open Counter Staff Portal"**.
2. When prompted for credentials, click the quick helper **"Fill Staff (staff123)"** and click **"Sign In"**.
3. Select **Counter 1 (Admissions)**.
4. **Call Next:**
   - Click **"📞 Call Next"**.
   - **Audio Announcement plays:** Chime sound followed by: *"Now serving token AD-003 at Counter 1"*.
   - Notice that Token **AD-003 (Charlie Davis, Emergency)** is called first, proving priority queue bypass!
   - Simultaneously look at the **Live Display Board** in the background — Counter 1 instantly flashes and updates to **AD-003**!
5. **Start Serving:**
   - Click **"▶ Start"**. Duration timer starts ticking (`00:01`, `00:02`). Token status transitions to `SERVING`.
6. **Complete:**
   - Click **"✔ Complete"**. Counter status returns to `ONLINE`. Today's served count increments to `1`.
7. **Demonstrate Auto-Call:**
   - Check the box **"Auto-Call Next when Free"**.
   - Within 3 seconds, `QueueScheduler` detects Counter 1 is free and automatically calls `AD-001 (Alice)` with chime!
8. **Demonstrate Skip (No-Show) & Recall:**
   - Click **"✖ Skip"**. Confirm dialog. Token `AD-001` transitions to `SKIPPED`.
   - Now click **"🔊 Recall"**. Token `AD-001` is recalled to the queue.
9. **Demonstrate Transfer:**
   - With a customer in attendance, click **"🔄 Transfer"**.
   - Select destination desk (e.g., `Library Desk`). Token is seamlessly transferred.

---

### Step 5: Admin Panel & Analytics (4:15 - 5:00)
1. In the Launcher, click **"Open Admin Panel"**.
2. Click **"Fill Admin (admin123)"** and sign in.
3. **Overview & Analytics Tab:**
   - Point out real-time KPI cards: Total Tokens, Completed, Skipped, No-show rate, Avg Wait Time, Avg Service Time.
   - Point out the **Tokens Processed per Service Department** Bar Chart.
   - Point out the **Hourly Queue Traffic (Peak Hours)** Bar Chart.
4. **Export CSV:**
   - Click **"📊 Export Report to CSV"** in the top header.
   - Save the file to desktop. Show that it contains all timestamps, wait times, and service durations.
5. **System Audit Trail Tab:**
   - Click the **"📜 System Audit Trail"** tab.
   - Show the row-by-row event log capturing every `CALL_NEXT`, `START_SERVING`, and `COMPLETED` with timestamps and operating usernames.

---

## 🎯 Concluding Summary Statement
> *"To conclude, our Smart Queue System handles end-to-end token life cycles without blocking the JavaFX thread, prevents concurrent race conditions with database row locking, provides real-time wait estimation, and gives staff and visitors complete transparency."*

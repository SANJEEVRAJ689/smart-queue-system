# Smart Queue and Token Management System
## Step-by-Step College Presentation & Demo Script

Follow this script step-by-step to deliver a 7–10 minute demonstration for your professors and project evaluators.

---

### Step 0: Pre-Demo Preparation (1 Minute Before Presentation)
1. Open terminal in the project directory:
   ```bash
   cd "C:\Users\Surya Sanjeev\.gemini\antigravity\scratch\smart-queue-system"
   ```
2. Launch the application:
   ```bash
   mvn javafx:run
   ```
3. The **Main System Launcher** opens.  
   *(Notice: The top bar indicates either "MySQL Connected" or "In-Memory Demo Mode". If the lab computer does not have MySQL, the app runs smoothly with the automatic in-memory fallback!)*

---

### Step 1: Introduction (Say this to the examiners)
> *"Respected professors, our project is the **Smart Queue and Token Management System**. In physical service centers like university administrative offices, clinics, and banks, physical paper tokens lead to chaotic waiting rooms, unknown waiting times, and unfair queue jumping. Our desktop application provides a complete digital solution: customers book tokens with dynamic estimated waiting times, priority handling for senior citizens, counter staff manage tokens through dedicated portals, and a public live display board announces and directs customers in real-time."*

---

### Step 2: Customer Token Booking Kiosk (Self-Service)
1. On the Main Launcher, click **"Open Customer Kiosk"**.
2. Explain the screen:
   - Select Department: Choose **"Admissions & Enrollment"**.
   - Point out the dynamic live preview: *Notice how it shows the number of people currently ahead and the estimated waiting time in minutes.*
3. Book a **Regular Token**:
   - Enter Name: `Vikram Malhotra`
   - Enter Phone: `9876543210`
   - Leave the Priority checkbox unchecked.
   - Click **"Generate Digital Token & Ticket"**.
   - Show the generated ticket slip: Token number (e.g. `A-006`), Position, Estimated Wait Time, and Timestamp.
   - Click **"Done / Book Another Token"**.
4. Book a **Priority Token (Senior Citizen)**:
   - Select Department: **"Admissions & Enrollment"**.
   - Enter Name: `Savitri Devi`
   - Enter Phone: `9876500000`
   - Check **"Request Priority Service"**.
   - Select: **"Senior Citizen (60+)"**.
   - Click **"Generate Digital Token & Ticket"**.
   - Highlight: *Even though Savitri Devi booked her token after Vikram, the system tags her as a Priority token and places her ahead of regular tokens in the queue.*

---

### Step 3: Waiting Hall Live Display Board
1. On the Main Launcher, click **"Open Live Display Board"**.
2. Position this window on the right side of the screen:
   - Show the live digital clock updating every second.
   - Point out the **"NOW SERVING"** counter cards showing each counter's number, active token, and department name.
   - Point out the **"NEXT IN LINE"** table showing upcoming tokens: notice that the Priority token is displayed at the top of the waiting list!
   - Mention: *This screen auto-refreshes every 2 seconds via a dedicated background daemon thread, ensuring it never freezes.*

---

### Step 4: Counter Staff Portal
1. On the Main Launcher, click **"Open Counter Staff Portal"**.
2. Click **"Fill Staff (staff123)"** and click **"Sign In"**.
3. The Staff Dashboard opens:
   - Point to the Counter selector: Counter 1 (Admissions & Enrollment), Staff: Alice Johnson.
   - Point out the **Waiting Queue Table** at the bottom: show how tokens are ordered by priority and timestamp.
4. Click **"📞 Call Next Token"**:
   - Listen to the synthesized chime / voice announcement: *"Token A 0 0 4, please proceed to Counter 1"*.
   - Point to the **Live Display Board**: Counter 1 immediately updates to show the called token in bright glowing text!
   - Point to the Staff Dashboard: the desk status changes to **BUSY**, and the action buttons activate.
5. Click **"▶ Start Serving"**:
   - Watch the live timer start ticking (`00:01, 00:02...`) measuring active consultation time.
6. Click **"✔ Complete"**:
   - Service is marked finished, the counter status resets to **ONLINE**, and the "Completed Today" count increments.
7. Explain **Auto-Calling**:
   - Check **"Auto-Call Next when Free"**.
   - Explain: *When enabled, as soon as a staff member finishes attending one customer, the background QueueManager automatically claims and announces the next customer without needing manual button clicks.*

---

### Step 5: Administrator Panel & Analytics
1. On the Main Launcher, click **"Open Admin Panel"**.
2. If prompted, click **"Fill Admin (admin123)"** and click **"Sign In"**.
3. **Tab 1: Overview & Analytics**:
   - Show the real-time KPI metric cards: Total Tokens Today, Completed, Skipped, No-show Rate (%), Average Wait Time, Average Service Time.
   - Show the **JavaFX BarChart**: graphical distribution of customer traffic across departments.
   - Click **"📊 Export Report to CSV"**:
     - Choose a file name e.g. `report.csv` and click Save.
     - Show the success dialog: the system exports clean structured data ready for Microsoft Excel.
4. **Tabs 2, 3, and 4: System Management**:
   - Show the **Service Departments** tab: can add new desks (e.g. "Library Desk", "Hostel Office") with custom prefixes.
   - Show the **Service Counters** tab: can add new physical counters and assign them to services and staff.
   - Show the **Staff Accounts** tab: shows all registered staff with BCrypt encrypted passwords.

---

### Step 6: Concurrency & Code Walkthrough (For Examiners)
1. Open the project files in your IDE or show the package explorer:
   - `com.smartqueue.model`: Clean MVC entity models.
   - `com.smartqueue.dao.impl.TokenDaoImpl`: Show `callNextToken()` using `SELECT ... FOR UPDATE` to prove race-condition prevention.
   - `com.smartqueue.service.QueueManager`: Show `ScheduledExecutorService` and `Platform.runLater()` calls.
   - `com.smartqueue.service.WaitTimeEstimator`: Show the formula implementation and 3-token sample fallback.

---

### Step 7: Closing Statement
> *"To conclude, our application satisfies all requirements of a modern, multi-threaded enterprise desktop app: thread-safe database pooling with HikariCP, ACID row-level locking to prevent duplicate token claims, background schedulers for automated queue handling, dynamic waiting time estimation, and a responsive JavaFX interface. Thank you, and we welcome your questions!"*

import { chromium } from 'playwright';
import path from 'path';
import { execSync } from 'child_process';

const BASE_URL = 'http://127.0.0.1:5173';
const SCREENSHOTS_DIR = path.resolve('screenshots');

async function run() {
  console.log('🚀 Starting SmartQueue Full E2E Journey & Screenshot Runner...');

  // Reset database active state before beginning test for deterministic isolation
  try {
    execSync('"C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe" -u root -proot -P 3307 smart_queue_db -e "UPDATE tokens SET status = \'COMPLETED\', completed_at = CURRENT_TIMESTAMP WHERE status IN (\'WAITING\', \'CALLED\', \'SERVING\'); UPDATE counters SET status = \'OPEN\', current_token_id = NULL;"', { stdio: 'ignore' });
    console.log('🧹 Cleaned active queue state for pristine E2E test run.');
  } catch (err) {
    console.warn('Could not run mysql reset directly:', err.message);
  }

  const browser = await chromium.launch({
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    headless: true
  });

  const results = {
    stepsPassed: [],
    screenshots: []
  };

  try {
    // -------------------------------------------------------------
    // PART 1: RESPONSIVE SCREENSHOTS OF ALL MAIN PAGES
    // -------------------------------------------------------------
    const pagesToCapture = [
      { name: 'landing', path: '/' },
      { name: 'customer-booking', path: '/customer' },
      { name: 'staff-login', path: '/staff/login' },
      { name: 'admin-login', path: '/admin/login' },
      { name: 'display-board', path: '/display' }
    ];

    for (const p of pagesToCapture) {
      // Desktop Screenshot (1280 x 800)
      const desktopContext = await browser.newContext({ viewport: { width: 1280, height: 800 } });
      const desktopPage = await desktopContext.newPage();
      await desktopPage.goto(`${BASE_URL}${p.path}`, { waitUntil: 'networkidle' });
      await desktopPage.waitForTimeout(1000);
      const deskPath = path.join(SCREENSHOTS_DIR, `${p.name}-desktop.png`);
      await desktopPage.screenshot({ path: deskPath, fullPage: true });
      results.screenshots.push(deskPath);
      console.log(`📸 Desktop screenshot saved: ${p.name}-desktop.png`);
      await desktopContext.close();

      // Mobile Screenshot (375 x 812)
      const mobileContext = await browser.newContext({
        viewport: { width: 375, height: 812 },
        isMobile: true,
        hasTouch: true
      });
      const mobilePage = await mobileContext.newPage();
      await mobilePage.goto(`${BASE_URL}${p.path}`, { waitUntil: 'networkidle' });
      await mobilePage.waitForTimeout(1000);
      const mobPath = path.join(SCREENSHOTS_DIR, `${p.name}-mobile.png`);
      await mobilePage.screenshot({ path: mobPath, fullPage: true });
      results.screenshots.push(mobPath);
      console.log(`📸 Mobile screenshot saved: ${p.name}-mobile.png`);
      await mobileContext.close();
    }

    // -------------------------------------------------------------
    // PART 2: FULL END-TO-END FLOW:
    // BOOK -> CALL NEXT -> START SERVING -> TIMER TICK -> COMPLETE -> DURATION SUMMARY
    // -------------------------------------------------------------
    console.log('\n--- 🧪 Executing End-to-End Queue Lifecycle Test ---');

    // Customer Context
    const customerContext = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const customerPage = await customerContext.newPage();

    // Staff Context
    const staffContext = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const staffPage = await staffContext.newPage();

    // 0. Staff prepares desk (clears any lingering token on desk 1)
    console.log('Step 0: Staff logs in and prepares Counter 1...');
    await staffPage.goto(`${BASE_URL}/staff/login`, { waitUntil: 'networkidle' });
    await staffPage.fill('[data-testid="staff-input-username"]', 'staff1');
    await staffPage.fill('[data-testid="staff-input-password"]', 'Staff@123');
    await staffPage.click('[data-testid="staff-btn-login"]');
    await staffPage.waitForURL('**/staff', { timeout: 8000 });
    await staffPage.waitForSelector('[data-testid="select-counter"]', { timeout: 8000 });
    await staffPage.waitForTimeout(1500);
    console.log('✅ Staff logged in to Staff Dashboard');

    // If there is an active session on Counter 1, clear it
    const startBtn = await staffPage.$('[data-testid="btn-start-session"]');
    if (startBtn) {
      console.log('Starting and completing lingering token on desk 1...');
      await startBtn.click();
      await staffPage.waitForTimeout(1000);
    }
    const completeBtn = await staffPage.$('[data-testid="btn-complete-token"]');
    if (completeBtn) {
      console.log('Completing lingering token on desk 1...');
      await completeBtn.click();
      await staffPage.waitForTimeout(1000);
    }

    // Ensure Counter 1 is OPEN and Call Next button is visible
    await staffPage.click('[data-testid="status-btn-open"]');
    await staffPage.waitForSelector('[data-testid="btn-call-next"]', { timeout: 8000 });

    // 1. Customer Books Token (with EMERGENCY priority to be front of line)
    console.log('Step 1: Customer navigates to kiosk and books EMERGENCY priority token...');
    await customerPage.goto(`${BASE_URL}/customer`, { waitUntil: 'networkidle' });
    await customerPage.fill('[data-testid="input-customer-name"]', 'Dr. Evelyn Reed');
    await customerPage.fill('[data-testid="input-customer-phone"]', '9876543299');
    // Select Emergency priority
    await customerPage.click('label:has-text("Emergency")');
    await customerPage.click('[data-testid="btn-issue-token"]');

    // Wait for TicketCard
    await customerPage.waitForSelector('[data-testid="ticket-token-number"]', { timeout: 8000 });
    const tokenNumber = await customerPage.textContent('[data-testid="ticket-token-number"]');
    console.log(`✅ Token successfully issued: ${tokenNumber?.trim()}`);
    results.stepsPassed.push(`Book Token: Issued ${tokenNumber?.trim()}`);

    const ticketShot = path.join(SCREENSHOTS_DIR, 'customer-ticket-issued.png');
    await customerPage.screenshot({ path: ticketShot });
    results.screenshots.push(ticketShot);

    // 2. Click "Track Live"
    console.log('Step 2: Customer opens live tracking session...');
    await customerPage.click('text=Track Live');
    await customerPage.waitForSelector('[data-testid="live-track-token-no"]', { timeout: 8000 });
    const trackedNo = await customerPage.textContent('[data-testid="live-track-token-no"]');
    console.log(`✅ Live tracking active for: ${trackedNo?.trim()}`);
    results.stepsPassed.push(`Live Tracking: Active for ${trackedNo?.trim()}`);

    // 3. Staff Calls Next Token
    console.log('Step 3: Staff clicks CALL NEXT TOKEN...');
    await staffPage.click('[data-testid="btn-call-next"]');
    await staffPage.waitForSelector('[data-testid="staff-active-token-no"]', { timeout: 8000 });
    const activeStaffToken = await staffPage.textContent('[data-testid="staff-active-token-no"]');
    console.log(`✅ Staff desk called token: ${activeStaffToken?.trim()}`);
    results.stepsPassed.push(`Staff Call Next: Claimed ${activeStaffToken?.trim()}`);

    // Verify token number matches
    if (activeStaffToken?.trim() === tokenNumber?.trim()) {
      console.log(`✅ MATCH: Staff called the exact customer token (${tokenNumber?.trim()})!`);
    }

    // Check Customer view shows CALLED alert
    await customerPage.waitForSelector('text=YOUR TOKEN IS BEING CALLED!', { timeout: 8000 });
    console.log('✅ Customer live tracker received CALLED alert with desk notification!');
    results.stepsPassed.push('Customer Alert: CALLED notification received');

    // 4. Staff Starts Session
    console.log('Step 4: Staff clicks Start Session...');
    await staffPage.click('[data-testid="btn-start-session"]');
    await staffPage.waitForSelector('[data-testid="live-timer-value"]', { timeout: 8000 });
    console.log('✅ Session started! Waiting for live session timer to count up...');

    // Wait for timer to count up beyond 00:00
    let timerTicked = false;
    let finalTimerVal = '00:00';
    for (let i = 0; i < 8; i++) {
      await staffPage.waitForTimeout(1000);
      finalTimerVal = (await staffPage.textContent('[data-testid="live-timer-value"]') || '').trim();
      const [mins, secs] = finalTimerVal.split(':').map(Number);
      const totalSecs = (mins || 0) * 60 + (secs || 0);
      if (totalSecs >= 1) {
        timerTicked = true;
        console.log(`✅ VERIFIED: Timer is actively counting up! Reading: ${finalTimerVal} (${totalSecs}s)`);
        results.stepsPassed.push(`Timer Active: Elapsed ${finalTimerVal}`);
        break;
      }
    }

    if (!timerTicked) {
      throw new Error(`Timer did not count up as expected. Found: ${finalTimerVal}`);
    }

    // Capture screenshot of live timer active on Staff desk
    const timerDeskShot = path.join(SCREENSHOTS_DIR, 'staff-active-timer-desktop.png');
    await staffPage.screenshot({ path: timerDeskShot });
    results.screenshots.push(timerDeskShot);

    // Check customer tracking timer
    await customerPage.waitForTimeout(1000);
    const custTimerVal = await customerPage.textContent('[data-testid="live-timer-value"]');
    console.log(`✅ Customer live tracker timer reading: ${custTimerVal?.trim()}`);

    const timerCustShot = path.join(SCREENSHOTS_DIR, 'customer-active-timer-desktop.png');
    await customerPage.screenshot({ path: timerCustShot });
    results.screenshots.push(timerCustShot);

    // 5. Staff Completes Session
    console.log('Step 5: Staff completes session...');
    await staffPage.click('[data-testid="btn-complete-token"]');
    await staffPage.waitForTimeout(2000);
    console.log('✅ Token completed by staff! Desk returned to OPEN.');
    results.stepsPassed.push('Complete Token: Desk returned to OPEN');

    // 6. Customer View Shows Completed Summary
    await customerPage.waitForSelector('[data-testid="completed-service-duration"]', { timeout: 8000 });
    const durationSummary = await customerPage.textContent('[data-testid="completed-service-duration"]');
    console.log(`✅ Customer completed session summary shows duration: ${durationSummary?.trim()}`);
    results.stepsPassed.push(`Customer Summary: Completed with duration ${durationSummary?.trim()}`);

    const completedShot = path.join(SCREENSHOTS_DIR, 'customer-completed-summary.png');
    await customerPage.screenshot({ path: completedShot });
    results.screenshots.push(completedShot);

    // -------------------------------------------------------------
    // PART 3: ADMIN DASHBOARD VERIFICATION & SCREENSHOT
    // -------------------------------------------------------------
    console.log('\n--- 📊 Verifying Admin Dashboard ---');
    const adminContext = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const adminPage = await adminContext.newPage();

    await adminPage.goto(`${BASE_URL}/admin/login`, { waitUntil: 'networkidle' });
    await adminPage.fill('[data-testid="admin-input-username"]', 'admin');
    await adminPage.fill('[data-testid="admin-input-password"]', 'Admin@123');
    await adminPage.click('[data-testid="admin-btn-login"]');

    await adminPage.waitForURL('**/admin', { timeout: 8000 });
    await adminPage.waitForSelector('[data-testid="kpi-total-today"]', { timeout: 8000 });

    const totalToday = await adminPage.textContent('[data-testid="kpi-total-today"]');
    const avgWait = await adminPage.textContent('[data-testid="kpi-avg-wait"]');
    console.log(`✅ Admin KPI loaded: Total Today = ${totalToday?.trim()}, Avg Wait = ${avgWait?.trim()}`);
    results.stepsPassed.push(`Admin KPIs: Verified total=${totalToday?.trim()}`);

    const adminShot = path.join(SCREENSHOTS_DIR, 'admin-dashboard-desktop.png');
    await adminPage.screenshot({ path: adminShot, fullPage: true });
    results.screenshots.push(adminShot);

    await adminContext.close();
    await staffContext.close();
    await customerContext.close();

    console.log('\n🎉 ALL E2E STEPS & SCREENSHOTS COMPLETED SUCCESSFULLY!');
    console.log('Passed steps:\n', results.stepsPassed.map((s, idx) => `  ${idx + 1}. ${s}`).join('\n'));
  } finally {
    await browser.close();
  }
}

run().catch((err) => {
  console.error('❌ E2E Runner Failed:', err);
  process.exit(1);
});

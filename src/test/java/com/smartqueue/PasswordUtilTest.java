package com.smartqueue;

import com.smartqueue.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordUtilTest {

    @Test
    public void testHashAndVerify() {
        String adminHash = PasswordUtil.hashPassword("Admin@123");
        String staffHash = PasswordUtil.hashPassword("Staff@123");

        assertTrue(PasswordUtil.checkPassword("Admin@123", adminHash));
        assertTrue(PasswordUtil.checkPassword("Staff@123", staffHash));

        System.out.println("ADMIN_HASH=" + adminHash);
        System.out.println("STAFF_HASH=" + staffHash);
    }
}

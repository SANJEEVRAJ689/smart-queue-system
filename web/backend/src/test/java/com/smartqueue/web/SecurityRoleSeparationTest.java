package com.smartqueue.web;

import com.smartqueue.web.dto.AuthDtos.LoginRequest;
import com.smartqueue.web.dto.AuthDtos.LoginResponse;
import com.smartqueue.web.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityRoleSeparationTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("Staff login accepts staff credentials and rejects admin accounts")
    void testStaffLoginRoleSeparation() {
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // 1. Staff credentials on staff login -> 200 OK
        LoginRequest staffReq = new LoginRequest("staff1", "Staff@123");
        LoginResponse staffRes = authService.loginStaff(staffReq, resp);
        assertNotNull(staffRes);
        assertEquals("STAFF", staffRes.getRole());
        assertNotNull(staffRes.getToken());

        // 2. Admin credentials on staff login -> 403 Forbidden
        LoginRequest adminReq = new LoginRequest("admin", "Admin@123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.loginStaff(adminReq, new MockHttpServletResponse());
        });
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Admin login accepts admin credentials and rejects staff accounts")
    void testAdminLoginRoleSeparation() {
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // 1. Admin credentials on admin login -> 200 OK
        LoginRequest adminReq = new LoginRequest("admin", "Admin@123");
        LoginResponse adminRes = authService.loginAdmin(adminReq, resp);
        assertNotNull(adminRes);
        assertEquals("ADMIN", adminRes.getRole());
        assertNotNull(adminRes.getToken());

        // 2. Staff credentials on admin login -> 403 Forbidden
        LoginRequest staffReq = new LoginRequest("staff1", "Staff@123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.loginAdmin(staffReq, new MockHttpServletResponse());
        });
        assertEquals(403, ex.getStatusCode().value());
    }
}

package com.smartqueue.web.service;

import com.smartqueue.web.dto.AuthDtos.LoginRequest;
import com.smartqueue.web.dto.AuthDtos.LoginResponse;
import com.smartqueue.web.entity.RefreshTokenEntity;
import com.smartqueue.web.entity.UserEntity;
import com.smartqueue.web.repository.UserRepository;
import com.smartqueue.web.security.CookieUtil;
import com.smartqueue.web.security.JwtTokenProvider;
import com.smartqueue.web.security.LoginAttemptService;
import com.smartqueue.web.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    @Value("${jwt.cookie-name:smartqueue_jwt}")
    private String jwtCookieName;

    @Value("${jwt.refresh-cookie-name:smartqueue_refresh}")
    private String refreshCookieName;

    @Value("${jwt.expiration-ms:900000}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public LoginResponse loginStaff(LoginRequest request, HttpServletResponse response) {
        return processLogin(request, "STAFF", response);
    }

    @Transactional
    public LoginResponse loginAdmin(LoginRequest request, HttpServletResponse response) {
        return processLogin(request, "ADMIN", response);
    }

    private LoginResponse processLogin(LoginRequest request, String expectedRole, HttpServletResponse response) {
        String username = request.getUsername().trim();
        if (loginAttemptService.isBlocked(username)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Account is temporarily locked due to 5 failed attempts. Please try again in 15 minutes.");
        }

        UserEntity user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> {
                    loginAttemptService.loginFailed(username);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.loginFailed(username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        // Role separation check
        if (!expectedRole.equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    String.format("User %s is not authorized for the %s portal. Please use the %s login.",
                            username, expectedRole, user.getRole()));
        }

        loginAttemptService.loginSucceeded(username);

        String jwt = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        String rawRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Set httpOnly cookies
        boolean isSecure = false; // set false for dev/localhost compatibility
        response.addHeader("Set-Cookie", CookieUtil.createHttpOnlyCookie(jwtCookieName, jwt, jwtExpirationMs / 1000, isSecure).toString());
        response.addHeader("Set-Cookie", CookieUtil.createHttpOnlyCookie(refreshCookieName, rawRefreshToken, refreshExpirationMs / 1000, isSecure).toString());

        return new LoginResponse(jwt, user.getRole(), user.getUsername(), user.getFullName());
    }

    @Transactional
    public LoginResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = CookieUtil.getCookieValue(request, refreshCookieName)
                .orElseGet(() -> request.getHeader("X-Refresh-Token"));

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing.");
        }

        RefreshTokenEntity entity = refreshTokenService.verifyAndRotate(rawRefreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid, expired, or revoked."));

        UserEntity user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User associated with refresh token not found."));

        String newJwt = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        boolean isSecure = false;
        response.addHeader("Set-Cookie", CookieUtil.createHttpOnlyCookie(jwtCookieName, newJwt, jwtExpirationMs / 1000, isSecure).toString());
        response.addHeader("Set-Cookie", CookieUtil.createHttpOnlyCookie(refreshCookieName, newRefreshToken, refreshExpirationMs / 1000, isSecure).toString());

        return new LoginResponse(newJwt, user.getRole(), user.getUsername(), user.getFullName());
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        CookieUtil.getCookieValue(request, refreshCookieName)
                .ifPresent(refreshTokenService::revokeToken);

        response.addHeader("Set-Cookie", CookieUtil.createClearCookie(jwtCookieName).toString());
        response.addHeader("Set-Cookie", CookieUtil.createClearCookie(refreshCookieName).toString());
    }
}

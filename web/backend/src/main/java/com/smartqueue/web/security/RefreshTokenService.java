package com.smartqueue.web.security;

import com.smartqueue.web.entity.RefreshTokenEntity;
import com.smartqueue.web.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Transactional
    public String createRefreshToken(Integer userId) {
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        Instant expiryDate = Instant.now().plusMillis(refreshExpirationMs);

        RefreshTokenEntity entity = new RefreshTokenEntity(userId, tokenHash, expiryDate);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional
    public Optional<RefreshTokenEntity> verifyAndRotate(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        Optional<RefreshTokenEntity> opt = refreshTokenRepository.findByTokenHash(hash);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        RefreshTokenEntity entity = opt.get();
        if (Boolean.TRUE.equals(entity.getRevoked()) || entity.getExpiryDate().isBefore(Instant.now())) {
            return Optional.empty();
        }

        // Revoke the old refresh token (rotation)
        entity.setRevoked(true);
        refreshTokenRepository.save(entity);

        return Optional.of(entity);
    }

    @Transactional
    public void revokeToken(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(e -> {
            e.setRevoked(true);
            refreshTokenRepository.save(e);
        });
    }
}

package com.smartqueue.concurrent;

import com.smartqueue.dao.AuditLogDao;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.model.Token;
import com.smartqueue.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * AutoCaller routes automated calling requests directly to the single canonical
 * call path in TokenService.callNextToken(counterId).
 *
 * Guarantees:
 * - One call path across manual staff clicks, background schedulers, and web APIs.
 * - Enforces counter row lock (SELECT ... FOR UPDATE) and prevents duplicate active tokens.
 * - Respects priority (EMERGENCY -> SENIOR -> (NORMAL waited >= 30m) -> NORMAL).
 */
public class AutoCaller {
    private static final Logger logger = LoggerFactory.getLogger(AutoCaller.class);

    private final TokenService tokenService;

    public AutoCaller() {
        this(new TokenService());
    }

    public AutoCaller(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Constructor for backward compatibility with existing tests.
     */
    public AutoCaller(TokenDao tokenDao, CounterDao counterDao, AuditLogDao auditLogDao) {
        this(new TokenService());
    }

    /**
     * Calls next token for the specified counter via the single canonical service path.
     */
    public Optional<Token> callNext(int counterId) {
        return tokenService.callNextToken(counterId);
    }

    /**
     * Backward-compatible overload routing to the single canonical call path.
     */
    public Optional<Token> callNext(int serviceId, int counterId) {
        return tokenService.callNextToken(counterId);
    }
}

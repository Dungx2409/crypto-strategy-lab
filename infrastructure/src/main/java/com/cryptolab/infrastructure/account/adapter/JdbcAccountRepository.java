package com.cryptolab.infrastructure.account.adapter;

import com.cryptolab.account.application.AccountConflictException;
import com.cryptolab.account.domain.Account;
import com.cryptolab.account.domain.AccountRole;
import com.cryptolab.account.domain.StoredAccount;
import com.cryptolab.account.port.AccountRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class JdbcAccountRepository implements AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoredAccount> findByNormalizedUsername(String normalizedUsername) {
        return jdbcTemplate.query(
                        """
                        SELECT id, username, password_hash, role, created_at
                        FROM accounts
                        WHERE normalized_username = ?
                        """,
                        this::storedAccount,
                        normalizedUsername)
                .stream()
                .findFirst();
    }

    @Override
    public Account create(
            UUID id,
            String username,
            String normalizedUsername,
            String passwordHash,
            AccountRole role,
            Instant createdAt) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO accounts (
                        id, username, normalized_username, password_hash, role, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    username,
                    normalizedUsername,
                    passwordHash,
                    role.name(),
                    OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
            return new Account(id, username, role, createdAt);
        } catch (DuplicateKeyException exception) {
            throw new AccountConflictException("Username is already registered");
        }
    }

    private StoredAccount storedAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        Account account = new Account(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                AccountRole.valueOf(resultSet.getString("role")),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
        return new StoredAccount(account, resultSet.getString("password_hash"));
    }
}

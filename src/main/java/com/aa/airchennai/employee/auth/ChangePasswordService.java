package com.aa.airchennai.employee.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangePasswordService
{
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public ChangePasswordService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ChangePasswordResult changePassword(
            long employeeId,
            String currentPassword,
            String newPassword,
            String confirmPassword)
    {
        if (isBlank(currentPassword)
                || isBlank(newPassword)
                || isBlank(confirmPassword))
        {
            return failure("All password fields are required.");
        }

        PasswordRecord passwordRecord;

        try
        {
            passwordRecord = jdbcTemplate.queryForObject(
                    """
                    SELECT password_hash, status
                    FROM employee
                    WHERE employee_id = ?
                    """,
                    (resultSet, rowNumber) -> new PasswordRecord(
                            resultSet.getString("password_hash"),
                            resultSet.getString("status")
                    ),
                    employeeId
            );
        }
        catch (EmptyResultDataAccessException exception)
        {
            return failure(
                    "Your account could not be found. Please sign in again."
            );
        }

        if (passwordRecord == null)
        {
            return failure(
                    "Your account could not be found. Please sign in again."
            );
        }

        if (!"ACTIVE".equals(passwordRecord.status()))
        {
            return failure(
                    "Your account is not active. Please contact an administrator."
            );
        }

        // Verify the current password against the stored BCrypt hash.
        if (!passwordEncoder.matches(
                currentPassword,
                passwordRecord.passwordHash()))
        {
            return failure("Your current password is incorrect.");
        }

        // Validate the new password on the server.
        if (!meetsPasswordRequirements(newPassword))
        {
            return failure(
                    "The new password must contain at least 12 characters, "
                            + "an uppercase letter, a lowercase letter, a number, "
                            + "and a special character."
            );
        }

        // The new password must differ from the current password.
        if (passwordEncoder.matches(
                newPassword,
                passwordRecord.passwordHash()))
        {
            return failure(
                    "The new password must differ from your current password."
            );
        }

        // The confirmation must exactly match the new password.
        if (!newPassword.equals(confirmPassword))
        {
            return failure(
                    "The new password and confirmation do not match."
            );
        }

        // Hash the new password before storing it.
        String newPasswordHash = passwordEncoder.encode(newPassword);

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE employee
                SET password_hash = ?,
                    must_change_password = FALSE
                WHERE employee_id = ?
                  AND password_hash = ?
                  AND status = 'ACTIVE'
                """,
                newPasswordHash,
                employeeId,
                passwordRecord.passwordHash()
        );

        if (updatedRows != 1)
        {
            return failure(
                    "Your account changed while processing the request. "
                            + "Please sign in again and retry."
            );
        }

        return new ChangePasswordResult(true, null);
    }

    private boolean meetsPasswordRequirements(String password)
    {
        return password != null
                && password.length() >= 12
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private ChangePasswordResult failure(String message)
    {
        return new ChangePasswordResult(false, message);
    }

    public record ChangePasswordResult(
            boolean successful,
            String message)
    {
    }

    private record PasswordRecord(
            String passwordHash,
            String status)
    {
    }
}
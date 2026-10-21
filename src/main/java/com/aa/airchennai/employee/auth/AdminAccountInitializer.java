package com.aa.airchennai.employee.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AdminAccountInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminAccountInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, Environment environment)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args)
    {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(726184, 1)");

        Boolean adminExists = jdbcTemplate.queryForObject("""
                              SELECT EXISTS (
                                    SELECT 1
                                    FROM employee
                                    WHERE DEPARTMENT = 'ADMIN'
                              )""",
                                    Boolean.class);

        if (Boolean.TRUE.equals(adminExists))
        {
            log.info("An ADMIN account already exists. Skipping bootstrap.");
            return;
        }

        String initialPassword = environment.getProperty("AIRCHENNAI_ADMIN_INITIAL_PASSWORD");

        if (!StringUtils.hasText(initialPassword))
        {
            throw new IllegalStateException("No ADMIN account exists. Configure "
                                                + "AIRCHENNAI_ADMIN_INITIAL_PASSWORD "
                                                + "before starting AirChennai."
            );
        }

        if (initialPassword.length() < 12)
        {
            throw new IllegalStateException("The initial ADMIN password must contain "
                                                + "at least 12 characters."
            );
        }

        String employeeCode = environment.getProperty("AIRCHENNAI_ADMIN_EMPLOYEE_CODE", "ADMIN001");
        String email = environment.getProperty("AIRCHENNAI_ADMIN_EMAIL", "admin@airchennai.local");
        String firstName = environment.getProperty("AIRCHENNAI_ADMIN_FIRST_NAME", "System");
        String lastName = environment.getProperty("AIRCHENNAI_ADMIN_LAST_NAME", "Administrator");
        String phoneNo = environment.getProperty("AIRCHENNAI_ADMIN_PHONE", "9000000000");

        String passwordHash = passwordEncoder.encode(initialPassword);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO employee (
                    employee_code,
                    email,
                    password_hash,
                    first_name,
                    last_name,
                    phone_no,
                    department,
                    status,
                    must_change_password,
                    hired_on
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, 'ADMIN', 'ACTIVE', TRUE,
                    CURRENT_DATE
                )
                ON CONFLICT DO NOTHING
                """,
                employeeCode,
                email,
                passwordHash,
                firstName,
                lastName,
                phoneNo
        );

        if (inserted == 1)
        {
            log.warn("Created initial ADMIN account with employee code {}. "
                            + "A password change is required at first login.",
                    employeeCode
            );
            return;
        }

        Boolean adminNowExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.employee
                    WHERE department = 'ADMIN'
                )
                """,
                Boolean.class
        );

        if (!Boolean.TRUE.equals(adminNowExists)) {
            throw new IllegalStateException("Could not create the initial ADMIN account. "
                                            + "Check employee code, email, and other "
                                            + "database constraints."
            );
        }

        log.info("An ADMIN account exists. No duplicate was created.");
    }
}
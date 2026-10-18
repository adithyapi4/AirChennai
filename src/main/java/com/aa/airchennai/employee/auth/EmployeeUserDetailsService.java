package com.aa.airchennai.employee.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class EmployeeUserDetailsService implements UserDetailsService
{
    private final JdbcTemplate jdbcTemplate;

    public EmployeeUserDetailsService(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String employeeCode)
    {
        try
        {
            return jdbcTemplate.queryForObject("""SELECT employee_id, employee_code, 
                                                    password_hash, department, status, 
                                                    must_change_password FROM employee
                                                    WHERE employee_code = ?""",
                    (resultSet, rowNumber) -> new EmployeeUserDetails(
                            resultSet.getLong("employee_id"),
                            resultSet.getString("employee_code"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("department"),
                            resultSet.getString("status"),
                            resultSet.getBoolean("must_change_password")
                    ),
                    employeeCode
            );

        }
        catch (EmptyResultDataAccessException exception)
        {
            throw new UsernameNotFoundException("Invalid employee ID or password");
        }
    }
}
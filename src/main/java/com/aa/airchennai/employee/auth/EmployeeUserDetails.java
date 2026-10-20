package com.aa.airchennai.employee.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class EmployeeUserDetails implements UserDetails
{
    private final String firstName;
    private final long employeeId;
    private final String employeeCode;
    private final String passwordHash;
    private final String department;
    private final String status;
    private final boolean mustChangePassword;

    public EmployeeUserDetails(
            long employeeId,
            String employeeCode,
            String firstName,
            String passwordHash,
            String department,
            String status,
            boolean mustChangePassword)
    {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.firstName = firstName;
        this.passwordHash = passwordHash;
        this.department = department;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
    }

    public long getEmployeeId() { return employeeId; }
    public String getFirstName() { return firstName; }
    public String getDepartment() { return department; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return List.of(new SimpleGrantedAuthority("ROLE_" + department));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return employeeCode; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return !"SUSPENDED".equals(status); }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return "ACTIVE".equals(status); }
}
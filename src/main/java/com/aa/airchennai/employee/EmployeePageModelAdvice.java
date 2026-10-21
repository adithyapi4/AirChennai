package com.aa.airchennai.employee;

import com.aa.airchennai.employee.auth.EmployeeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.aa.airchennai.employee")
public class EmployeePageModelAdvice
{
    @ModelAttribute("employeeFirstName")
    public String employeeFirstName()
    {
        EmployeeUserDetails employee = currentEmployee();
        return employee != null ? employee.getFirstName() : "";
    }

    @ModelAttribute("employeeDashboardUrl")
    public String employeeDashboardUrl()
    {
        EmployeeUserDetails employee = currentEmployee();

        return employee != null ? dashboardPath(employee.getDepartment()) : "/employee/login";
    }

    @ModelAttribute("showHomeButton")
    public boolean showHomeButton(HttpServletRequest request)
    {
        EmployeeUserDetails employee = currentEmployee();

        if (employee == null)
        {
            return false;
        }

        String contextPath = request.getContextPath();
        String currentPath = request.getRequestURI().substring(contextPath.length());

        return !currentPath.equals(dashboardPath(employee.getDepartment()));
    }

    private EmployeeUserDetails currentEmployee()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal()
                instanceof EmployeeUserDetails employee)
        {
            return employee;
        }

        return null;
    }

    private String dashboardPath(String department)
    {
        return switch (department)
        {
            case "ADMIN" -> "/employee/admin/dashboard";
            case "HR" -> "/employee/hr/dashboard";
            case "COUNTER" -> "/employee/counter/dashboard";
            case "OPERATIONS" -> "/employee/operations/dashboard";
            case "FLEET" -> "/employee/fleet/dashboard";
            default -> "/employee/dashboard";
        };
    }
}
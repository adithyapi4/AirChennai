package com.aa.airchennai.employee.hr;

import com.aa.airchennai.employee.auth.EmployeeUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController
{
    @GetMapping("/employee/hr/dashboard")
    public String dashboard()
    {
        return "employee/hr-dashboard";
    }
}
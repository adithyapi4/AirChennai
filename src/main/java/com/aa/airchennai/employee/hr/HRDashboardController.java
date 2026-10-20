package com.aa.airchennai.employee.hr;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HRDashboardController
{
    @GetMapping("/employee/hr/dashboard")
    public String dashboard()
    {
        return "employee/hr-dashboard";
    }
}
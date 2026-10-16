package com.aa.airchennai.employee.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmployeeLoginController
{
    @GetMapping("/employee/login")
    public String login()
    {
        return "auth/employee-login";
    }
}
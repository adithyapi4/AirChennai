package com.aa.airchennai.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the static-ish pages: home, employee login, customer registration. */
@Controller
public class PageController
{
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /** Login dropdown -> "Customer Login" opens the registration page. */
    @GetMapping("/customer/register")
    public String customerRegister(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "register";
    }
}

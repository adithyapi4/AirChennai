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

}

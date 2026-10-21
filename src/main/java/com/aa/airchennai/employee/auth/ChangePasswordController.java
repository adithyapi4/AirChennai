
package com.aa.airchennai.employee.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChangePasswordController
{
    private static final String CHANGE_PASSWORD_VIEW =
            "auth/change-password";

    private final ChangePasswordService changePasswordService;

    private final SecurityContextLogoutHandler logoutHandler =
            new SecurityContextLogoutHandler();

    public ChangePasswordController(
            ChangePasswordService changePasswordService)
    {
        this.changePasswordService = changePasswordService;
    }

    @GetMapping("/employee/change-password")
    public String showChangePasswordPage()
    {
        return CHANGE_PASSWORD_VIEW;
    }

    @PostMapping("/employee/change-password")
    public String changePassword(
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee,
            @RequestParam(value = "currentPassword", required = false)
            String currentPassword,
            @RequestParam(value = "newPassword", required = false)
            String newPassword,
            @RequestParam(value = "confirmPassword", required = false)
            String confirmPassword,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response)
    {
        if (currentEmployee == null)
        {
            return "redirect:/employee/login";
        }

        ChangePasswordService.ChangePasswordResult result =
                changePasswordService.changePassword(
                        currentEmployee.getEmployeeId(),
                        currentPassword,
                        newPassword,
                        confirmPassword
                );

        if (!result.successful())
        {
            model.addAttribute("errorMessage", result.message());
            model.addAttribute("passwordChanged", false);

            return CHANGE_PASSWORD_VIEW;
        }

        model.addAttribute("passwordChanged", true);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        logoutHandler.logout(request, response, authentication);

        return CHANGE_PASSWORD_VIEW;
    }
}

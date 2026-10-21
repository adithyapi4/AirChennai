
package com.aa.airchennai.employee.hr;

import com.aa.airchennai.employee.auth.EmployeeUserDetails;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/employee/hr/employees")
public class RemoveEmployeeController
{
    private final JdbcTemplate jdbcTemplate;

    public RemoveEmployeeController(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/remove")
    public String showRemoveEmployeePage()
    {
        return "employee/hr/remove-employee";
    }

    @PostMapping("/remove/lookup")
    public String lookupEmployee(
            @RequestParam("employeeCode") String employeeCode,
            Model model,
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee)
    {
        String code = normalizeEmployeeCode(employeeCode);
        model.addAttribute("employeeCode", code);

        if (!isValidEmployeeCode(code))
        {
            model.addAttribute("errors", List.of("Enter a valid employee code.")
            );
            return "employee/hr/remove-employee";
        }

        EmployeeDetails employee = findEmployee(code);

        if (employee == null) {
            model.addAttribute(
                    "errors",
                    List.of("No employee was found with code " + code + ".")
            );
            return "employee/hr/remove-employee";
        }

        model.addAttribute("employee", employee);

        String restriction = removalRestriction(employee, currentEmployee);
        model.addAttribute("canRemove", restriction == null);

        if (restriction != null)
        {
            model.addAttribute("removalRestriction", restriction);
        }

        return "employee/hr/remove-employee";
    }

    @PostMapping("/remove/confirm")
    public String removeEmployee(
            @RequestParam("employeeCode") String employeeCode,
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee,
            RedirectAttributes redirectAttributes)
    {
        String code = normalizeEmployeeCode(employeeCode);

        if (!isValidEmployeeCode(code))
        {
            redirectAttributes.addFlashAttribute(
                    "removeError", "Enter a valid employee code."
            );
            return "redirect:/employee/hr/employees/remove";
        }

        // Query the database again. Do not trust details or permissions
        // submitted by the browser.
        EmployeeDetails employee = findEmployee(code);

        if (employee == null)
        {
            redirectAttributes.addFlashAttribute(
                    "removeError",
                    "No employee was found with code " + code + "."
            );
            return "redirect:/employee/hr/employees/remove";
        }

        String restriction = removalRestriction(employee, currentEmployee);

        if (restriction != null)
        {
            redirectAttributes.addFlashAttribute("removeError", restriction);
            return "redirect:/employee/hr/employees/remove";
        }

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE employee
                SET status = 'TERMINATED'
                WHERE employee_code = ?
                  AND status <> 'TERMINATED'
                  AND department <> 'ADMIN'
                  AND employee_id <> ?
                """,
                code,
                currentEmployee.getEmployeeId()
        );

        if (updatedRows != 1) {
            redirectAttributes.addFlashAttribute(
                    "removeError",
                    "The employee could not be removed. "
                            + "Their status may have changed. Please try again."
            );
            return "redirect:/employee/hr/employees/remove";
        }

        redirectAttributes.addFlashAttribute("removedEmployeeCode", code);

        return "redirect:/employee/hr/employees/remove";
    }

    private EmployeeDetails findEmployee(String employeeCode)
    {
        try
        {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        e.employee_id,
                        e.employee_code,
                        e.first_name,
                        e.last_name,
                        e.email,
                        e.phone_no,
                        e.department,
                        e.status,
                        e.hired_on,
                        e.base_airport_code,
                        a.name AS base_airport_name,
                        a.city AS base_airport_city,
                        a.active AS base_airport_active
                    FROM employee e
                    LEFT JOIN airport a
                        ON a.code = e.base_airport_code
                    WHERE e.employee_code = ?
                    """,
                    (resultSet, rowNumber) -> mapEmployee(resultSet),
                    employeeCode
            );
        }
        catch (EmptyResultDataAccessException exception)
        {
            return null;
        }
    }

    private EmployeeDetails mapEmployee(ResultSet resultSet) throws SQLException
    {
        java.sql.Date hiredOn = resultSet.getDate("hired_on");
        String airportCode = resultSet.getString("base_airport_code");

        Boolean airportActive = (Boolean) resultSet.getObject("base_airport_active");

        return new EmployeeDetails(
                resultSet.getLong("employee_id"),
                resultSet.getString("employee_code"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("email"),
                resultSet.getString("phone_no"),
                resultSet.getString("department"),
                resultSet.getString("status"),
                hiredOn.toString(),
                airportCode == null ? null : airportCode.trim(),
                resultSet.getString("base_airport_name"),
                resultSet.getString("base_airport_city"),
                airportActive
        );
    }

    private String removalRestriction(EmployeeDetails target, EmployeeUserDetails currentEmployee)
    {
        if (target.getEmployeeId() == currentEmployee.getEmployeeId())
        {
            return "You cannot remove your own employee account.";
        }

        if ("ADMIN".equals(target.getDepartment()))
        {
            return "Administrator accounts cannot be removed through HR.";
        }

        if ("TERMINATED".equals(target.getStatus()))
        {
            return "This employee is already terminated.";
        }

        return null;
    }

    private String normalizeEmployeeCode(String employeeCode)
    {
        return employeeCode == null ? "" : employeeCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isValidEmployeeCode(String employeeCode)
    {
        return employeeCode.matches("[A-Z0-9_-]{3,20}");
    }

    public static class EmployeeDetails
    {
        private final long employeeId;
        private final String employeeCode;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final String phoneNo;
        private final String department;
        private final String status;
        private final String hiredOn;
        private final String baseAirportCode;
        private final String baseAirportName;
        private final String baseAirportCity;
        private final Boolean baseAirportActive;

        public EmployeeDetails(
                long employeeId,
                String employeeCode,
                String firstName,
                String lastName,
                String email,
                String phoneNo,
                String department,
                String status,
                String hiredOn,
                String baseAirportCode,
                String baseAirportName,
                String baseAirportCity,
                Boolean baseAirportActive)
        {
            this.employeeId = employeeId;
            this.employeeCode = employeeCode;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phoneNo = phoneNo;
            this.department = department;
            this.status = status;
            this.hiredOn = hiredOn;
            this.baseAirportCode = baseAirportCode;
            this.baseAirportName = baseAirportName;
            this.baseAirportCity = baseAirportCity;
            this.baseAirportActive = baseAirportActive;
        }

        public long getEmployeeId() { return employeeId; }
        public String getEmployeeCode() { return employeeCode; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email; }
        public String getPhoneNo() { return phoneNo; }
        public String getDepartment() { return department; }
        public String getStatus() { return status; }
        public String getHiredOn() { return hiredOn; }
        public String getBaseAirportCode() { return baseAirportCode; }
        public String getBaseAirportName() { return baseAirportName; }
        public String getBaseAirportCity() {return baseAirportCity; }
        public Boolean getBaseAirportActive() { return baseAirportActive; }
    }
}

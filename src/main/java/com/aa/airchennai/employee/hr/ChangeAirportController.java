
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
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/employee/hr/airports")
public class ChangeAirportController
{
    private final JdbcTemplate jdbcTemplate;

    public ChangeAirportController(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public String showChangeAirportPage()
    {
        return "employee/hr/change-airport";
    }

    // Step 1: Find an employee by employee code.
    @PostMapping("/lookup")
    public String lookupEmployee(
            @RequestParam("employeeCode") String employeeCode,
            Model model,
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee)
    {
        String code = normalizeEmployeeCode(employeeCode);
        model.addAttribute("employeeCode", code);

        if (!isValidEmployeeCode(code))
        {
            model.addAttribute(
                    "errors",
                    List.of("Enter a valid employee code.")
            );

            return "employee/hr/change-airport";
        }

        EmployeeDetails employee = findEmployee(code);

        if (employee == null)
        {
            model.addAttribute(
                    "errors",
                    List.of("No employee was found with code " + code + ".")
            );

            return "employee/hr/change-airport";
        }

        addEmployeeToModel(employee, model);

        return "employee/hr/change-airport";
    }

    // Step 2: Preview the selected airport before confirming.
    @PostMapping("/preview")
    public String previewAirport(
            @RequestParam("employeeCode") String employeeCode,
            @RequestParam("airportCode") String airportCode,
            Model model,
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee)
    {
        String code = normalizeEmployeeCode(employeeCode);
        String selectedCode = normalizeAirportCode(airportCode);

        model.addAttribute("employeeCode", code);

        if (!isValidEmployeeCode(code))
        {
            model.addAttribute(
                    "errors",
                    List.of("Enter a valid employee code.")
            );

            return "employee/hr/change-airport";
        }

        EmployeeDetails employee = findEmployee(code);

        if (employee == null)
        {
            model.addAttribute(
                    "errors",
                    List.of("No employee was found with code " + code + ".")
            );

            return "employee/hr/change-airport";
        }

        addEmployeeToModel(employee, model);

        if ("TERMINATED".equals(employee.getStatus()))
        {
            return "employee/hr/change-airport";
        }

        if (!selectedCode.matches("[A-Z]{3}"))
        {
            model.addAttribute(
                    "errors",
                    List.of("Select a valid IATA airport code.")
            );

            return "employee/hr/change-airport";
        }

        AirportOption airport = findActiveAirport(selectedCode);

        if (airport == null)
        {
            model.addAttribute(
                    "errors",
                    List.of("The selected airport does not exist or is inactive.")
            );

            return "employee/hr/change-airport";
        }

        if (selectedCode.equals(employee.getBaseAirportCode()))
        {
            model.addAttribute(
                    "errors",
                    List.of("The employee is already assigned to this airport.")
            );

            return "employee/hr/change-airport";
        }

        model.addAttribute("selectedAirport", airport);

        return "employee/hr/change-airport";
    }

    // Step 3: Confirm and save the airport change.
    @PostMapping("/change")
    public String changeAirport(
            @RequestParam("employeeCode") String employeeCode,
            @RequestParam("airportCode") String airportCode,
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee,
            RedirectAttributes redirectAttributes)
    {
        String code = normalizeEmployeeCode(employeeCode);
        String selectedCode = normalizeAirportCode(airportCode);

        if (!isValidEmployeeCode(code) || !selectedCode.matches("[A-Z]{3}"))
        {
            redirectAttributes.addFlashAttribute(
                    "changeError",
                    "The submitted employee or airport code is invalid."
            );

            return "redirect:/employee/hr/airports";
        }

        // Recheck the employee and airport on the server.
        EmployeeDetails employee = findEmployee(code);

        if (employee == null)
        {
            redirectAttributes.addFlashAttribute(
                    "changeError",
                    "The employee could not be found."
            );

            return "redirect:/employee/hr/airports";
        }

        if ("TERMINATED".equals(employee.getStatus()))
        {
            redirectAttributes.addFlashAttribute(
                    "changeError",
                    "The airport cannot be changed for a terminated employee."
            );

            return "redirect:/employee/hr/airports";
        }

        AirportOption airport = findActiveAirport(selectedCode);

        if (airport == null)
        {
            redirectAttributes.addFlashAttribute(
                    "changeError",
                    "The selected airport does not exist or is inactive."
            );

            return "redirect:/employee/hr/airports";
        }

        if (selectedCode.equals(employee.getBaseAirportCode()))
        {
            redirectAttributes.addFlashAttribute(
                    "changeError",
                    "The employee is already assigned to this airport."
            );

            return "redirect:/employee/hr/airports";
        }

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE employee
                SET base_airport_code = ?
                WHERE employee_code = ?
                  AND status <> 'TERMINATED'
                  AND base_airport_code IS DISTINCT FROM ?
                  AND EXISTS (
                      SELECT 1
                      FROM airport
                      WHERE code = ?
                        AND active = TRUE
                  )
                """,
                selectedCode,
                code,
                selectedCode,
                selectedCode
        );

        if (updatedRows != 1)
        {
            redirectAttributes.addFlashAttribute(
                    "changeError",
                    "The airport could not be changed. Please search again."
            );

            return "redirect:/employee/hr/airports";
        }

        redirectAttributes.addFlashAttribute(
                "changedEmployeeCode",
                code
        );

        redirectAttributes.addFlashAttribute(
                "changedAirportLabel",
                airport.getCode() + " — " + airport.getName()
                        + ", " + airport.getCity()
                        + ", " + airport.getCountry()
        );

        return "redirect:/employee/hr/airports";
    }

    private void addEmployeeToModel(
            EmployeeDetails employee,
            Model model)
    {
        model.addAttribute("employee", employee);

        boolean canChange =
                !"TERMINATED".equals(employee.getStatus());

        model.addAttribute("canChangeAirport", canChange);

        if (canChange)
        {
            model.addAttribute(
                    "activeAirportCodes",
                    findActiveAirportCodes()
            );
        }
        else
        {
            model.addAttribute(
                    "changeRestriction",
                    "The airport cannot be changed for a terminated employee."
            );
        }
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

    private EmployeeDetails mapEmployee(ResultSet resultSet)
            throws SQLException
    {
        String airportCode = resultSet.getString("base_airport_code");
        Boolean airportActive =
                (Boolean) resultSet.getObject("base_airport_active");

        return new EmployeeDetails(
                resultSet.getLong("employee_id"),
                resultSet.getString("employee_code"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("email"),
                resultSet.getString("phone_no"),
                resultSet.getString("department"),
                resultSet.getString("status"),
                airportCode == null ? null : airportCode.trim(),
                resultSet.getString("base_airport_name"),
                resultSet.getString("base_airport_city"),
                airportActive
        );
    }

    private List<String> findActiveAirportCodes()
    {
        return jdbcTemplate.query(
                """
                SELECT code
                FROM airport
                WHERE active = TRUE
                ORDER BY code
                """,
                (resultSet, rowNumber) ->
                        resultSet.getString("code").trim()
        );
    }

    private AirportOption findActiveAirport(String airportCode)
    {
        try
        {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT code, name, city, country
                    FROM airport
                    WHERE code = ?
                    AND active = TRUE
                    """,
                    (resultSet, rowNumber) -> new AirportOption(
                            resultSet.getString("code").trim(),
                            resultSet.getString("name"),
                            resultSet.getString("city"),
                            resultSet.getString("country")
                    ),
                    airportCode
            );
        }
        catch (EmptyResultDataAccessException exception)
        {
            return null;
        }
    }

    private String normalizeEmployeeCode(String employeeCode)
    {
        return employeeCode == null
                ? ""
                : employeeCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAirportCode(String airportCode)
    {
        return airportCode == null
                ? ""
                : airportCode.trim().toUpperCase(Locale.ROOT);
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
            this.baseAirportCode = baseAirportCode;
            this.baseAirportName = baseAirportName;
            this.baseAirportCity = baseAirportCity;
            this.baseAirportActive = baseAirportActive;
        }

        public long getEmployeeId() { return employeeId; }
        public String getEmployeeCode() { return employeeCode; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email;}
        public String getPhoneNo() { return phoneNo; }
        public String getDepartment() { return department; }
        public String getStatus() { return status; }
        public String getBaseAirportCode() { return baseAirportCode; }
        public String getBaseAirportName() { return baseAirportName; }
        public String getBaseAirportCity() { return baseAirportCity;}
        public Boolean getBaseAirportActive() { return baseAirportActive; }
    }

    public static class AirportOption
    {
        private final String code;
        private final String name;
        private final String city;
        private final String country;

        public AirportOption(String code, String name, String city, String country)
        {
            this.code = code;
            this.name = name;
            this.city = city;
            this.country = country;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getCity() { return city; }
        public String getCountry() { return country; }
    }
}

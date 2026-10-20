package com.aa.airchennai.employee.hr;

import com.aa.airchennai.employee.auth.EmployeeUserDetails;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/employee/hr/employees")
public class AddEmployeeController
{
    private static final List<String> ALLOWED_DEPARTMENTS =
            List.of("HR", "COUNTER", "OPERATIONS", "FLEET");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String UPPERCASE =
            "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final String LOWERCASE =
            "abcdefghijkmnopqrstuvwxyz";

    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AddEmployeeController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/add")
    public String showAddEmployeeForm(Model model)
    {
        if (!model.containsAttribute("form"))
        {
            model.addAttribute("form", new EmployeeForm());
        }

        model.addAttribute("allowedDepartments", ALLOWED_DEPARTMENTS);

        return "employee/hr/add-employee";
    }

    @PostMapping("/add")
    public String addEmployee(
            @ModelAttribute("form") EmployeeForm form,
            Model model,
            RedirectAttributes redirectAttributes,
            @AuthenticationPrincipal EmployeeUserDetails currentEmployee) {

        model.addAttribute("allowedDepartments", ALLOWED_DEPARTMENTS);

        // Defence in depth: this operation is for HR employees only.
        if (currentEmployee == null || !"HR".equals(currentEmployee.getDepartment()))
        {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only HR employees can add employees."
            );
        }

        List<String> errors = validate(form);

        if (!errors.isEmpty())
        {
            model.addAttribute("errors", errors);
            return "employee/hr/add-employee";
        }

        String employeeCode = clean(form.getEmployeeCode()).toUpperCase(Locale.ROOT);

        String email = clean(form.getEmail()).toLowerCase(Locale.ROOT);

        String firstName = clean(form.getFirstName());
        String lastName = clean(form.getLastName());
        String phoneNo = clean(form.getPhoneNo());

        String department = clean(form.getDepartment()).toUpperCase(Locale.ROOT);

        String temporaryPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(temporaryPassword);

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO employee (
                        employee_code,
                        email,
                        password_hash,
                        first_name,
                        last_name,
                        phone_no,
                        department,
                        status,
                        hired_on,
                        created_by,
                        must_change_password
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?,
                        'ACTIVE',
                        CURRENT_DATE,
                        ?,
                        TRUE
                    )
                    """,
                    employeeCode,
                    email,
                    passwordHash,
                    firstName,
                    lastName,
                    phoneNo,
                    department,
                    currentEmployee.getEmployeeId()
            );

        }
        catch (DuplicateKeyException exception)
        {
            errors.add("That employee code or email address already exists.");
            model.addAttribute("errors", errors);

            return "employee/hr/add-employee";
        }

        // Show the temporary password once, after successful creation.
        redirectAttributes.addFlashAttribute("createdEmployeeCode", employeeCode);

        redirectAttributes.addFlashAttribute("temporaryPassword", temporaryPassword);

        return "redirect:/employee/hr/employees/add";
    }

    private List<String> validate(EmployeeForm form) {
        List<String> errors = new ArrayList<>();

        String employeeCode = clean(form.getEmployeeCode()).toUpperCase(Locale.ROOT);

        String email = clean(form.getEmail());
        String firstName = clean(form.getFirstName());
        String lastName = clean(form.getLastName());
        String phoneNo = clean(form.getPhoneNo());

        String department = clean(form.getDepartment()).toUpperCase(Locale.ROOT);

        if (!employeeCode.matches("[A-Z0-9_-]{3,20}"))
        {
            errors.add("Employee code must contain 3–20 letters, numbers, "
                            + "hyphens, or underscores."
            );
        }

        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
        {
            errors.add("Enter a valid email address.");
        }

        if (firstName.isBlank() || firstName.length() > 100)
        {
            errors.add("First name is required and must not exceed 100 characters.");
        }

        if (lastName.isBlank() || lastName.length() > 100)
        {
            errors.add("Last name is required and must not exceed 100 characters.");
        }

        if (!phoneNo.matches("[6-9][0-9]{9}"))
        {
            errors.add("Enter a valid 10-digit Indian mobile number.");
        }

        if (!ALLOWED_DEPARTMENTS.contains(department))
        {
            errors.add("Select a valid department.");
        }

        return errors;
    }

    private String generateTemporaryPassword()
    {
        List<Character> characters = new ArrayList<>();

        // Guarantee at least one character from each required category.
        characters.add(randomCharacter(UPPERCASE));
        characters.add(randomCharacter(LOWERCASE));
        characters.add(randomCharacter(DIGITS));
        characters.add(randomCharacter(SPECIAL));

        String allCharacters = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

        // Generate a 16-character password.
        while (characters.size() < 16)
        {
            characters.add(randomCharacter(allCharacters));
        }

        // Shuffle so the required categories are not in fixed positions.
        Collections.shuffle(characters, RANDOM);

        StringBuilder password = new StringBuilder();

        for (char character : characters)
        {
            password.append(character);
        }

        return password.toString();
    }

    private char randomCharacter(String characters)
    {
        return characters.charAt(RANDOM.nextInt(characters.length()));
    }

    private String clean(String value)
    {
        return value == null ? "" : value.trim();
    }

    public static class EmployeeForm
    {
        private String employeeCode;
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNo;
        private String department;

        public String getEmployeeCode() { return employeeCode; }
        public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPhoneNo() { return phoneNo; }
        public void setPhoneNo(String phoneNo) { this.phoneNo = phoneNo; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
    }
}

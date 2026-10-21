package com.aa.airchennai.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;

/** Form data collected before the customer selects a seat. */
public class PassengerDetailsForm implements Serializable {
    @NotBlank(message = "First name is required.")
    @Size(max = 100, message = "First name must be at most 100 characters.")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "First name contains unsupported characters.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    @Size(max = 100, message = "Last name must be at most 100 characters.")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Last name contains unsupported characters.")
    private String lastName;

    @NotNull(message = "Date of birth is required.")
    @Past(message = "Date of birth must be in the past.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @NotBlank(message = "Please select a gender.")
    @Pattern(regexp = "M|F", message = "Please select a valid gender.")
    private String gender;

    @NotBlank(message = "Nationality is required.")
    @Size(max = 100, message = "Nationality must be at most 100 characters.")
    private String nationality = "Indian";

    @NotBlank(message = "Passenger type is required.")
    @Pattern(regexp = "ADULT|CHILD|INFANT", message = "Please select a valid passenger type.")
    private String passengerType = "ADULT";

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName == null ? null : firstName.trim(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName == null ? null : lastName.trim(); }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality == null ? null : nationality.trim(); }
    public String getPassengerType() { return passengerType; }
    public void setPassengerType(String passengerType) { this.passengerType = passengerType; }
}

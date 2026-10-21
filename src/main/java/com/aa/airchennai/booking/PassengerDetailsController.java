package com.aa.airchennai.booking;

import com.aa.airchennai.flight.FlightSearchService;
import com.aa.airchennai.flight.FlightSearchService.CabinFare;
import com.aa.airchennai.flight.FlightSearchService.FlightOption;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Controller
public class PassengerDetailsController {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FlightSearchService flights;
    private final PassengerDetailsService passengers;

    public PassengerDetailsController(FlightSearchService flights, PassengerDetailsService passengers) {
        this.flights = flights;
        this.passengers = passengers;
    }

    @GetMapping("/booking/passenger-details")
    public String showForm(@RequestParam("flightInstanceId") long flightInstanceId,
                           @RequestParam(name = "cabin", defaultValue = "ECONOMY") String cabin,
                           HttpSession session,
                           Model model) {
        Optional<FlightOption> flight = bookable(flightInstanceId);
        Optional<CabinFare> fare = flight.flatMap(f -> fareFor(f, cabin));
        if (flight.isEmpty() || fare.isEmpty()) {
            return "redirect:/";
        }

        PassengerDetailsForm existing = passengers.get(session);
        model.addAttribute("passengerDetails", existing == null ? new PassengerDetailsForm() : existing);
        model.addAttribute("flight", flight.get());
        model.addAttribute("fare", fare.get());
        return "passenger-details";
    }

    @PostMapping("/booking/passenger-details")
    public String submit(@RequestParam("flightInstanceId") long flightInstanceId,
                         @RequestParam(name = "cabin", defaultValue = "ECONOMY") String cabin,
                         @Valid @ModelAttribute("passengerDetails") PassengerDetailsForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model) {
        Optional<FlightOption> flight = bookable(flightInstanceId);
        Optional<CabinFare> fare = flight.flatMap(f -> fareFor(f, cabin));
        if (flight.isEmpty() || fare.isEmpty()) {
            return "redirect:/";
        }

        if (form.getDateOfBirth() != null && !form.getDateOfBirth().isBefore(java.time.LocalDate.now(IST))) {
            bindingResult.rejectValue("dateOfBirth", "future", "Date of birth must be before today.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("flight", flight.get());
            model.addAttribute("fare", fare.get());
            return "passenger-details";
        }

        passengers.save(session, form);
        return "redirect:/booking/seats?flightInstanceId=" + flightInstanceId + "&cabin=" + fare.get().code();
    }

    private Optional<FlightOption> bookable(long flightInstanceId) {
        return flights.findById(flightInstanceId)
                .filter(f -> "SCHEDULED".equals(f.status()) || f.isDelayed())
                .filter(f -> f.departureAt().isAfter(LocalDateTime.now(IST)));
    }

    private static Optional<CabinFare> fareFor(FlightOption flight, String cabin) {
        return flight.fares().stream()
                .filter(c -> c.code().equalsIgnoreCase(cabin) && c.isAvailable())
                .findFirst();
    }
}

package com.aa.airchennai.booking;

import com.aa.airchennai.flight.FlightSearchService;
import com.aa.airchennai.flight.FlightSearchService.CabinFare;
import com.aa.airchennai.flight.FlightSearchService.FlightOption;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Landing page for the "Book" button on the search results.
 * For now it only confirms which flight + cabin was picked; passenger details,
 * seat selection and payment are the next step to build.
 */
@Controller
public class BookingController
{
    private final FlightSearchService flights;

    public BookingController(FlightSearchService flights) {
        this.flights = flights;
    }

    @GetMapping("/booking/new")
    public String start(@RequestParam("flightInstanceId") long flightInstanceId,
                        @RequestParam("cabin") String cabin,
                        Model model) {
        Optional<FlightOption> flight = flights.findById(flightInstanceId);
        Optional<CabinFare> fare = flight.flatMap(f -> f.fares().stream()
                .filter(c -> c.code().equals(cabin) && c.isAvailable())
                .findFirst());

        if (flight.isEmpty() || fare.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("flight", flight.get());
        model.addAttribute("fare", fare.get());
        return "booking-start";
    }
}

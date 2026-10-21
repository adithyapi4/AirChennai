package com.aa.airchennai.flight;

import com.aa.airchennai.flight.FlightSearchService.AirportInfo;
import com.aa.airchennai.flight.FlightSearchService.CabinFare;
import com.aa.airchennai.flight.FlightSearchService.ConnectingOption;
import com.aa.airchennai.flight.FlightSearchService.FlightOption;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * GET /flights/search?from=MAA&to=BOM&departureDate=2026-11-10[&tripType=ROUND_TRIP&returnDate=...]
 * Target of the Search button on the home page.
 */
@Controller
public class FlightSearchController
{
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FlightSearchService flights;

    public FlightSearchController(FlightSearchService flights) {
        this.flights = flights;
    }

    @GetMapping("/flights/search")
    public String search(@RequestParam(name = "from", defaultValue = "") String from,
                         @RequestParam(name = "to", defaultValue = "") String to,
                         @RequestParam(name = "departureDate", defaultValue = "") String departureDate,
                         @RequestParam(name = "tripType", defaultValue = "ONE_WAY") String tripType,
                         @RequestParam(name = "returnDate", defaultValue = "") String returnDate,
                         Model model) {

        String fromCode = from.trim().toUpperCase(Locale.ROOT);
        String toCode = to.trim().toUpperCase(Locale.ROOT);
        LocalDate date = parseDate(departureDate);
        boolean round = "ROUND_TRIP".equals(tripType);
        LocalDate returnOn = round ? parseDate(returnDate) : null;

        Optional<AirportInfo> origin = flights.findAirport(fromCode);
        Optional<AirportInfo> destination = flights.findAirport(toCode);

        // Incomplete / tampered query string: send the user back to the booking box.
        if (origin.isEmpty() || destination.isEmpty() || date == null
                || fromCode.equals(toCode) || (round && (returnOn == null || returnOn.isBefore(date)))) {
            return "redirect:/";
        }

        LocalDate today = LocalDate.now(IST);

        model.addAttribute("origin", origin.get());
        model.addAttribute("destination", destination.get());
        model.addAttribute("departureDate", date);
        model.addAttribute("tripType", round ? "ROUND_TRIP" : "ONE_WAY");
        model.addAttribute("returnDate", returnOn);
        // Direct flights first; only when there are none, fall back to one-stop connections.
        var outbound = flights.search(fromCode, toCode, date);
        var outboundConnections = outbound.isEmpty()
                ? flights.searchConnections(fromCode, toCode, date)
                : List.<ConnectingOption>of();
        model.addAttribute("outbound", outbound);
        model.addAttribute("outboundConnections", outboundConnections);

        var inbound = round ? flights.search(toCode, fromCode, returnOn) : List.<FlightOption>of();
        model.addAttribute("inbound", round ? inbound : null);
        model.addAttribute("inboundConnections",
                round && inbound.isEmpty() ? flights.searchConnections(toCode, fromCode, returnOn) : List.of());

        // Target of the "Book ticket" button: the first bookable flight of the outbound journey
        // (the first leg when the journey is a connection).
        model.addAttribute("bookTarget", bookTarget(outbound, outboundConnections));

        // Previous / next day shortcuts (one-way only; never before today).
        model.addAttribute("prevDate", !round && date.isAfter(today) ? date.minusDays(1) : null);
        model.addAttribute("nextDate", !round ? date.plusDays(1) : null);
        return "search-results";
    }

    /** Where the "Book ticket" button goes: a flight instance and the cheapest-first cabin with seats left. */
    public record BookTarget(long flightInstanceId, String cabin) {}

    private static BookTarget bookTarget(List<FlightOption> direct, List<ConnectingOption> connections) {
        for (FlightOption f : direct) {
            BookTarget t = targetFor(f);
            if (t != null) return t;
        }
        for (ConnectingOption c : connections) {
            BookTarget t = targetFor(c.first());
            if (t != null) return t;
        }
        return null;
    }

    private static BookTarget targetFor(FlightOption f) {
        if (f.flightInstanceId() == null) {
            return null;
        }
        return f.fares().stream()
                .filter(CabinFare::isAvailable)
                .findFirst()
                .map(c -> new BookTarget(f.flightInstanceId(), c.code()))
                .orElse(null);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeException e) {
            return null;
        }
    }
}

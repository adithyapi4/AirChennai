package com.aa.airchennai.booking;

import com.aa.airchennai.flight.FlightSearchService;
import com.aa.airchennai.flight.FlightSearchService.CabinFare;
import com.aa.airchennai.flight.FlightSearchService.FlightOption;
import com.aa.airchennai.booking.SeatSelectionService.HeldSeat;
import com.aa.airchennai.booking.SeatSelectionService.SeatUnavailableException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * "Book ticket" flow:
 *   GET  /booking/seats    seat map for a flight instance
 *   POST /booking/seats    hold the chosen seat, then go to payment
 *   GET  /booking/payment  payment page for the held seat
 */
@Controller
public class SeatSelectionController
{
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String TOKEN_ATTR = "seatHoldToken";

    private final FlightSearchService flights;
    private final SeatSelectionService seats;
    private final PassengerDetailsService passengerDetails;

    public SeatSelectionController(FlightSearchService flights, SeatSelectionService seats, PassengerDetailsService passengerDetails) {
        this.flights = flights;
        this.seats = seats;
        this.passengerDetails = passengerDetails;
    }

    @GetMapping("/booking/seats")
    public String seatMap(@RequestParam("flightInstanceId") long flightInstanceId,
                          @RequestParam(name = "cabin", defaultValue = "ECONOMY") String cabin,
                          HttpSession session,
                          Model model) {
        Optional<FlightOption> flight = bookable(flightInstanceId);
        Optional<CabinFare> fare = flight.flatMap(f -> fareFor(f, cabin));
        if (fare.isEmpty()) {
            return "redirect:/";
        }
        if (!passengerDetails.exists(session)) {
            return "redirect:/booking/passenger-details?flightInstanceId=" + flightInstanceId + "&cabin=" + fare.get().code();
        }
        String token = token(session);

        model.addAttribute("flight", flight.get());
        model.addAttribute("fare", fare.get());
        model.addAttribute("sections", seats.seatMap(flightInstanceId, fare.get().code(), token));
        model.addAttribute("holdMinutes", SeatSelectionService.HOLD_MINUTES);
        return "seat-selection";
    }

    @PostMapping("/booking/seats")
    public String holdSeat(@RequestParam("flightInstanceId") long flightInstanceId,
                           @RequestParam("cabin") String cabin,
                           @RequestParam("seat") String seat,
                           HttpSession session,
                           RedirectAttributes redirect) {
        Optional<FlightOption> flight = bookable(flightInstanceId);
        Optional<CabinFare> fare = flight.flatMap(f -> fareFor(f, cabin));
        if (fare.isEmpty()) {
            return "redirect:/";
        }
        String seatsUrl = "redirect:/booking/seats?flightInstanceId=" + flightInstanceId + "&cabin=" + fare.get().code();
        try {
            seats.holdSeat(flightInstanceId, fare.get().code(), seat.trim().toUpperCase(Locale.ROOT), token(session));
        } catch (SeatUnavailableException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return seatsUrl;
        }
        return "redirect:/booking/payment?flightInstanceId=" + flightInstanceId;
    }

    @GetMapping("/booking/payment")
    public String payment(@RequestParam("flightInstanceId") long flightInstanceId,
                          HttpSession session,
                          Model model,
                          RedirectAttributes redirect) {
        Optional<FlightOption> flight = bookable(flightInstanceId);
        Optional<HeldSeat> hold = seats.findHold(flightInstanceId, token(session));
        if (flight.isEmpty()) {
            return "redirect:/";
        }
        if (hold.isEmpty()) {
            redirect.addFlashAttribute("error", "Your seat hold has expired. Please choose a seat again.");
            return "redirect:/booking/seats?flightInstanceId=" + flightInstanceId;
        }
        Optional<CabinFare> fare = fareFor(flight.get(), hold.get().cabin());
        if (fare.isEmpty()) {
            return "redirect:/";
        }

        BigDecimal seatFee = hold.get().seatFee();
        BigDecimal total = fare.get().price().add(seatFee);

        model.addAttribute("flight", flight.get());
        model.addAttribute("fare", fare.get());
        model.addAttribute("hold", hold.get());
        model.addAttribute("seatFeeText", seatFee.signum() > 0 ? flights.formatPrice(seatFee) : null);
        model.addAttribute("totalText", flights.formatPrice(total));
        return "payment";
    }

    // ------------------------------------------------------------------ helpers

    /** The flight must exist, still be on sale and not have departed yet. */
    private Optional<FlightOption> bookable(long flightInstanceId) {
        return flights.findById(flightInstanceId)
                .filter(f -> "SCHEDULED".equals(f.status()) || f.isDelayed())
                .filter(f -> f.departureAt().isAfter(LocalDateTime.now(IST)));
    }

    private static Optional<CabinFare> fareFor(FlightOption flight, String cabin) {
        return flight.fares().stream()
                .filter(c -> c.code().equals(cabin) && c.isAvailable())
                .findFirst();
    }

    /** One random id per browser session; it identifies who owns a seat hold. */
    private static String token(HttpSession session) {
        Object existing = session.getAttribute(TOKEN_ATTR);
        if (existing instanceof String s) {
            return s;
        }
        String created = UUID.randomUUID().toString();
        session.setAttribute(TOKEN_ATTR, created);
        return created;
    }
}

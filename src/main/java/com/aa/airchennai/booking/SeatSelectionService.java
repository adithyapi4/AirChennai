package com.aa.airchennai.booking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seat map + seat holds for one flight_instance.
 *
 * Seat state is stored per flight instance in flight_seat (one row per physical seat, keyed by
 * flight_instance_id) and the cabin counters in flight_instance_cabin_inventory are kept in step:
 *   AVAILABLE -> HELD   (customer picks a seat; held_seats + 1)
 *   HELD      -> AVAILABLE when the hold expires or the customer picks another seat (held_seats - 1)
 * BOOKED / BLOCKED seats (and seats held by someone else) are shown grey and cannot be picked.
 */
@Service
public class SeatSelectionService
{
    public static final int HOLD_MINUTES = 10;

    private static final Map<String, String> CABIN_LABELS = Map.of(
            "ECONOMY", "Economy",
            "PREMIUM_ECONOMY", "Premium Economy",
            "BUSINESS", "Business",
            "FIRST", "First");

    /** One seat icon on the map. state: FREE, MINE (held by this browser session) or TAKEN. */
    public record Seat(String number, String letter, String cabin, String positionLabel,
                       String state, boolean selectable) {
        public boolean isTaken() { return "TAKEN".equals(state); }
        public boolean isMine() { return "MINE".equals(state); }
        public String getCss() {
            if (isTaken()) return "seat taken";
            if (!selectable) return "seat locked";
            return isMine() ? "seat free selected" : "seat free";
        }
        public String getStateLabel() {
            if (isTaken()) return "unavailable";
            if (!selectable) return "not in your cabin";
            return isMine() ? "selected" : "available";
        }
    }

    public record SeatRow(int number, boolean exitRow, List<Seat> left, List<Seat> right) {}

    public record CabinSection(String cabin, String label, boolean selectable, long freeSeats, List<SeatRow> rows) {}

    public record HeldSeat(String seatNumber, String cabin, BigDecimal seatFee, OffsetDateTime heldUntil) {
        public long getHeldUntilEpochMs() { return heldUntil.toInstant().toEpochMilli(); }
    }

    /** Thrown (and rolls the transaction back) when a seat cannot be held. */
    public static class SeatUnavailableException extends RuntimeException {
        public SeatUnavailableException(String message) { super(message); }
    }

    private record RawSeat(String number, int row, String letter, String cabin, String position,
                           boolean exitRow, boolean extraLegroom,
                           String status, String token, OffsetDateTime heldUntil) {}

    private final JdbcClient jdbc;

    public SeatSelectionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ seat map

    @Transactional
    public List<CabinSection> seatMap(long flightInstanceId, String selectedCabin, String token) {
        ensureSeats(flightInstanceId);
        releaseExpiredHolds(flightInstanceId);

        List<RawSeat> raw = jdbc.sql("""
                        SELECT fs.seat_number, sl.row_number, sl.seat_letter, fs.cabin_class, sl.seat_position,
                               sl.is_exit_row, sl.extra_legroom,
                               fs.status, fs.selection_token, fs.held_until
                        FROM flight_seat fs
                        JOIN flight_instance fi ON fi.flight_instance_id = fs.flight_instance_id
                        JOIN seat_layout sl     ON sl.aircraft_type_id = fi.aircraft_type_id
                                               AND sl.row_number::text || sl.seat_letter = fs.seat_number
                        WHERE fs.flight_instance_id = :fi
                        ORDER BY sl.row_number, sl.column_position
                        """)
                .param("fi", flightInstanceId)
                .query((rs, n) -> new RawSeat(
                        rs.getString("seat_number"),
                        rs.getInt("row_number"),
                        rs.getString("seat_letter").trim(),
                        rs.getString("cabin_class"),
                        rs.getString("seat_position"),
                        rs.getBoolean("is_exit_row"),
                        rs.getBoolean("extra_legroom"),
                        rs.getString("status"),
                        rs.getString("selection_token"),
                        rs.getObject("held_until", OffsetDateTime.class)))
                .list();

        OffsetDateTime now = OffsetDateTime.now();
        Map<Integer, List<RawSeat>> byRow = new LinkedHashMap<>();
        for (RawSeat r : raw) {
            byRow.computeIfAbsent(r.row(), k -> new ArrayList<>()).add(r);
        }

        Map<String, List<SeatRow>> byCabin = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<RawSeat>> e : byRow.entrySet()) {
            List<RawSeat> rowSeats = e.getValue();
            List<Seat> seats = rowSeats.stream().map(r -> toSeat(r, selectedCabin, token, now)).toList();
            int half = seats.size() / 2;
            RawSeat first = rowSeats.get(0);
            byCabin.computeIfAbsent(first.cabin(), k -> new ArrayList<>())
                    .add(new SeatRow(e.getKey(), first.exitRow(),
                            seats.subList(0, half), seats.subList(half, seats.size())));
        }

        List<CabinSection> sections = new ArrayList<>();
        byCabin.forEach((cabin, rows) -> {
            long free = rows.stream()
                    .flatMap(r -> java.util.stream.Stream.concat(r.left().stream(), r.right().stream()))
                    .filter(s -> !s.isTaken())
                    .count();
            sections.add(new CabinSection(cabin, CABIN_LABELS.getOrDefault(cabin, cabin),
                    cabin.equals(selectedCabin), free, rows));
        });
        return sections;
    }

    private static Seat toSeat(RawSeat r, String selectedCabin, String token, OffsetDateTime now) {
        String state;
        if ("AVAILABLE".equals(r.status())) {
            state = "FREE";
        } else if ("HELD".equals(r.status()) && r.token() != null) {
            if (r.heldUntil() != null && !r.heldUntil().isAfter(now)) {
                state = "FREE";                       // expired hold
            } else {
                state = r.token().equals(token) ? "MINE" : "TAKEN";
            }
        } else {
            state = "TAKEN";                          // BOOKED, BLOCKED, or held for a booking
        }

        String position = switch (r.position()) {
            case "WINDOW" -> "Window";
            case "AISLE" -> "Aisle";
            default -> "Middle";
        };
        if (r.exitRow()) position += " · Exit row";
        else if (r.extraLegroom()) position += " · Extra legroom";

        boolean selectable = r.cabin().equals(selectedCabin) && !"TAKEN".equals(state);
        return new Seat(r.number(), r.letter(), r.cabin(), position, state, selectable);
    }

    // ------------------------------------------------------------------ holds

    /**
     * Holds one seat for this browser session for {@value #HOLD_MINUTES} minutes. Any other seat the
     * same session was holding on this flight is released. The claim is a single conditional UPDATE,
     * so two people clicking the same seat cannot both get it.
     */
    @Transactional
    public HeldSeat holdSeat(long flightInstanceId, String cabin, String seatNumber, String token) {
        ensureSeats(flightInstanceId);
        releaseExpiredHolds(flightInstanceId);

        int claimed = jdbc.sql("""
                        UPDATE flight_seat
                           SET status = 'HELD', selection_token = :token,
                               held_until = now() + make_interval(mins => :mins)
                         WHERE flight_instance_id = :fi AND seat_number = :seat
                           AND cabin_class = :cabin AND status = 'AVAILABLE'
                        """)
                .param("token", token).param("mins", HOLD_MINUTES)
                .param("fi", flightInstanceId).param("seat", seatNumber).param("cabin", cabin)
                .update();

        if (claimed == 1) {
            releaseHolds(flightInstanceId, token, seatNumber);
            int counted = jdbc.sql("""
                            UPDATE flight_instance_cabin_inventory
                               SET held_seats = held_seats + 1
                             WHERE flight_instance_id = :fi AND cabin_class = :cabin
                               AND total_seats - held_seats - confirmed_seats - blocked_seats > 0
                            """)
                    .param("fi", flightInstanceId).param("cabin", cabin)
                    .update();
            if (counted == 0) {
                throw new SeatUnavailableException("This cabin is sold out.");
            }
        } else {
            // Already held by this same session? Then just extend the hold.
            int refreshed = jdbc.sql("""
                            UPDATE flight_seat
                               SET held_until = now() + make_interval(mins => :mins)
                             WHERE flight_instance_id = :fi AND seat_number = :seat AND cabin_class = :cabin
                               AND status = 'HELD' AND booking_id IS NULL AND selection_token = :token
                            """)
                    .param("mins", HOLD_MINUTES).param("fi", flightInstanceId)
                    .param("seat", seatNumber).param("cabin", cabin).param("token", token)
                    .update();
            if (refreshed == 0) {
                throw new SeatUnavailableException("Seat " + seatNumber + " is no longer available. Please choose another seat.");
            }
        }
        return findHold(flightInstanceId, token)
                .orElseThrow(() -> new SeatUnavailableException("Could not hold the seat. Please try again."));
    }

    /** The unexpired seat this browser session is holding on the flight, if any. */
    public Optional<HeldSeat> findHold(long flightInstanceId, String token) {
        return jdbc.sql("""
                        SELECT seat_number, cabin_class, seat_fee, held_until
                        FROM flight_seat
                        WHERE flight_instance_id = :fi AND status = 'HELD' AND booking_id IS NULL
                          AND selection_token = :token AND held_until > now()
                        """)
                .param("fi", flightInstanceId).param("token", token)
                .query((rs, n) -> new HeldSeat(
                        rs.getString("seat_number"),
                        rs.getString("cabin_class"),
                        rs.getBigDecimal("seat_fee"),
                        rs.getObject("held_until", OffsetDateTime.class)))
                .optional();
    }

    // ------------------------------------------------------------------ internals

    /** Creates the flight_seat rows for this flight instance from the aircraft's seat_layout (first use only). */
    private void ensureSeats(long flightInstanceId) {
        Integer existing = jdbc.sql("SELECT count(*) FROM flight_seat WHERE flight_instance_id = :fi")
                .param("fi", flightInstanceId)
                .query(Integer.class).single();
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO flight_seat (flight_instance_id, seat_number, cabin_class)
                        SELECT fi.flight_instance_id, sl.row_number::text || sl.seat_letter, sl.cabin_class
                        FROM flight_instance fi
                        JOIN seat_layout sl ON sl.aircraft_type_id = fi.aircraft_type_id
                        WHERE fi.flight_instance_id = :fi
                        ON CONFLICT (flight_instance_id, seat_number) DO NOTHING
                        """)
                .param("fi", flightInstanceId)
                .update();
    }

    /** Frees seat-map holds whose time has run out and gives the capacity back to the cabin inventory. */
    private void releaseExpiredHolds(long flightInstanceId) {
        jdbc.sql("""
                        WITH released AS (
                            UPDATE flight_seat
                               SET status = 'AVAILABLE', selection_token = NULL, held_until = NULL
                             WHERE flight_instance_id = :fi AND status = 'HELD'
                               AND booking_id IS NULL AND selection_token IS NOT NULL AND held_until <= now()
                            RETURNING cabin_class
                        ), cnt AS (
                            SELECT cabin_class, count(*) AS n FROM released GROUP BY cabin_class
                        )
                        UPDATE flight_instance_cabin_inventory inv
                           SET held_seats = GREATEST(inv.held_seats - cnt.n, 0)
                          FROM cnt
                         WHERE inv.flight_instance_id = :fi AND inv.cabin_class = cnt.cabin_class
                        """)
                .param("fi", flightInstanceId)
                .update();
    }

    /** Releases every other seat this session holds on the flight (used when it picks a different seat). */
    private void releaseHolds(long flightInstanceId, String token, String exceptSeat) {
        jdbc.sql("""
                        WITH released AS (
                            UPDATE flight_seat
                               SET status = 'AVAILABLE', selection_token = NULL, held_until = NULL
                             WHERE flight_instance_id = :fi AND status = 'HELD' AND booking_id IS NULL
                               AND selection_token = :token AND seat_number <> :seat
                            RETURNING cabin_class
                        ), cnt AS (
                            SELECT cabin_class, count(*) AS n FROM released GROUP BY cabin_class
                        )
                        UPDATE flight_instance_cabin_inventory inv
                           SET held_seats = GREATEST(inv.held_seats - cnt.n, 0)
                          FROM cnt
                         WHERE inv.flight_instance_id = :fi AND inv.cabin_class = cnt.cabin_class
                        """)
                .param("fi", flightInstanceId).param("token", token).param("seat", exceptSeat)
                .update();
    }
}

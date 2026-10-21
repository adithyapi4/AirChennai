package com.aa.airchennai.flight;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Finds bookable departures for an origin / destination / date.
 * Everything shown on the results page (times, price, seats left) comes from
 * flight_instance, flight_fare and flight_instance_cabin_inventory.
 */
@Service
public class FlightSearchService
{
    public record AirportInfo(String code, String name, String city) {}

    public record CabinFare(String code, String label, String priceText, int seatsLeft, BigDecimal price) {
        public boolean isAvailable() { return seatsLeft > 0; }
        public boolean isFewLeft() { return seatsLeft > 0 && seatsLeft <= 9; }
    }

    public record FlightOption(
            Long flightInstanceId,
            String flightNumber,
            AirportInfo origin,
            AirportInfo destination,
            String departureTime,
            String departureDate,
            String arrivalTime,
            String arrivalDayNote,
            String duration,
            String status,
            String aircraft,
            String terminal,
            List<CabinFare> fares,
            String lowestPriceText,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt) {
        public boolean isDelayed() { return "DELAYED".equals(status); }
        public boolean isBookable() { return fares.stream().anyMatch(CabinFare::isAvailable); }
        public boolean isFaresPublished() { return !fares.isEmpty(); }
    }

    /**
     * A one-stop itinerary: two separate flights from the flight table,
     * origin -> hub and hub -> destination.
     */
    public record ConnectingOption(AirportInfo hub,
                                   FlightOption first,
                                   FlightOption second,
                                   String layover,
                                   String totalDuration) {}

    /** Shortest / longest layover accepted when joining two flights. */
    private static final int MIN_LAYOVER_MIN = 60;
    private static final int MAX_LAYOVER_MIN = 10 * 60;
    private static final int MAX_CONNECTIONS = 1;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private static final Map<String, String> CABIN_LABELS = Map.of(
            "ECONOMY", "Economy",
            "PREMIUM_ECONOMY", "Premium Economy",
            "BUSINESS", "Business",
            "FIRST", "First");

    private static final Map<String, Integer> CABIN_ORDER = Map.of(
            "ECONOMY", 1, "PREMIUM_ECONOMY", 2, "BUSINESS", 3, "FIRST", 4);

    private static final String FLIGHT_SELECT = """
            SELECT fi.flight_instance_id,
                   f.flight_number,
                   o.code AS o_code, o.name AS o_name, o.city AS o_city,
                   d.code AS d_code, d.name AS d_name, d.city AS d_city,
                   (fi.estimated_departure_at AT TIME ZONE o.timezone) AS dep_local,
                   (fi.estimated_arrival_at   AT TIME ZONE d.timezone) AS arr_local,
                   (extract(epoch FROM (fi.estimated_arrival_at - fi.estimated_departure_at)) / 60)::int AS dur_min,
                   fi.status,
                   fi.terminal,
                   t.manufacturer || ' ' || t.model AS aircraft
            FROM flight_instance fi
            JOIN flight f         ON f.flight_id = fi.flight_id
            JOIN route r          ON r.route_id = f.route_id
            JOIN airport o        ON o.code = r.origin_code
            JOIN airport d        ON d.code = r.destination_code
            JOIN aircraft_type t  ON t.aircraft_type_id = fi.aircraft_type_id
            """;

    /** Intermediate row before fares are attached. */
    private record FlightRow(Long id, String number, AirportInfo origin, AirportInfo destination,
                             LocalDateTime dep, LocalDateTime arr, int durationMin,
                             String status, String terminal, String aircraft) {}

    private final JdbcClient jdbc;
    private final NumberFormat rupees;

    public FlightSearchService(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.rupees = NumberFormat.getIntegerInstance(Locale.of("en", "IN"));
    }

    public Optional<AirportInfo> findAirport(String code) {
        return jdbc.sql("SELECT code, name, city FROM airport WHERE active AND code = :code")
                .param("code", code)
                .query((rs, n) -> new AirportInfo(rs.getString("code").trim(), rs.getString("name"), rs.getString("city")))
                .optional();
    }

    /**
     * Direct flights from {@code from} to {@code to} on {@code date}.
     * The search runs on the recurring {@code flight} schedule: the route must match and the
     * flight's days_of_week bitmask must include the date's weekday. When a dated departure
     * (flight_instance) exists its estimated times / status / fares are used; otherwise the
     * scheduled local times from {@code flight} are shown and no fares are listed.
     */
    public List<FlightOption> search(String from, String to, LocalDate date) {
        List<FlightRow> rows = jdbc.sql("""
                        SELECT fi.flight_instance_id,
                               f.flight_number,
                               o.code AS o_code, o.name AS o_name, o.city AS o_city,
                               d.code AS d_code, d.name AS d_name, d.city AS d_city,
                               COALESCE(fi.estimated_departure_at AT TIME ZONE o.timezone,
                                        CAST(:date AS date) + f.departure_time_local) AS dep_local,
                               COALESCE(fi.estimated_arrival_at AT TIME ZONE d.timezone,
                                        CAST(:date AS date) + f.arrival_day_offset + f.arrival_time_local) AS arr_local,
                               (COALESCE(extract(epoch FROM (fi.estimated_arrival_at - fi.estimated_departure_at)),
                                         extract(epoch FROM ((CAST(:date AS date) + f.arrival_day_offset + f.arrival_time_local)
                                                           - (CAST(:date AS date) + f.departure_time_local)))) / 60)::int AS dur_min,
                               COALESCE(fi.status, 'SCHEDULED') AS status,
                               fi.terminal,
                               t.manufacturer || ' ' || t.model AS aircraft
                        FROM flight f
                        JOIN route r         ON r.route_id = f.route_id
                        JOIN airport o       ON o.code = r.origin_code
                        JOIN airport d       ON d.code = r.destination_code
                        LEFT JOIN flight_instance fi
                               ON fi.flight_id = f.flight_id AND fi.flight_date = CAST(:date AS date)
                        JOIN aircraft_type t ON t.aircraft_type_id = COALESCE(fi.aircraft_type_id, f.aircraft_type_id)
                        WHERE r.origin_code = :from
                          AND r.destination_code = :to
                          AND r.active AND f.active
                          AND (f.days_of_week & (1 << (extract(isodow FROM CAST(:date AS date))::int - 1))) <> 0
                          AND (fi.flight_instance_id IS NULL OR fi.status IN ('SCHEDULED', 'DELAYED'))
                          AND (CAST(:date AS date) + f.departure_time_local) > (now() AT TIME ZONE o.timezone)
                        ORDER BY f.departure_time_local, f.flight_number
                        """)
                .param("from", from)
                .param("to", to)
                .param("date", date)
                .query(FlightSearchService::mapRow)
                .list();
        return withFares(rows);
    }

    /**
     * One-stop options for {@code from} -> {@code to} on {@code date}, for use when there is no
     * direct flight. Both legs are ordinary rows of the {@code flight} table: a hub is any airport
     * that has an active route from {@code from} AND an active route to {@code to}; the second
     * flight must leave between 60 minutes and 10 hours after the first one lands.
     * For each first flight the earliest valid second flight is kept; results are ordered by
     * final arrival time and only the best (earliest-arriving) one-stop option is returned.
     */
    public List<ConnectingOption> searchConnections(String from, String to, LocalDate date) {
        List<String> hubs = jdbc.sql("""
                        SELECT DISTINCT r1.destination_code
                        FROM route r1
                        JOIN route r2 ON r2.origin_code = r1.destination_code
                        WHERE r1.origin_code = :from
                          AND r2.destination_code = :to
                          AND r1.active AND r2.active
                          AND r1.destination_code NOT IN (:from, :to)
                        """)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> rs.getString(1).trim())
                .list();

        List<ConnectingOption> result = new ArrayList<>();
        for (String hubCode : hubs) {
            Optional<AirportInfo> hub = findAirport(hubCode);
            if (hub.isEmpty()) {
                continue;
            }
            List<FlightOption> firstLegs = search(from, hubCode, date);
            Map<LocalDate, List<FlightOption>> secondLegsByDay = new LinkedHashMap<>();

            for (FlightOption first : firstLegs) {
                LocalDate landing = first.arrivalAt().toLocalDate();
                // The connection may cross midnight, so look at the landing day and the next one.
                List<FlightOption> candidates = new ArrayList<>();
                for (LocalDate day : List.of(landing, landing.plusDays(1))) {
                    candidates.addAll(secondLegsByDay.computeIfAbsent(day, d -> search(hubCode, to, d)));
                }
                candidates.stream()
                        .filter(second -> {
                            long layover = Duration.between(first.arrivalAt(), second.departureAt()).toMinutes();
                            return layover >= MIN_LAYOVER_MIN && layover <= MAX_LAYOVER_MIN;
                        })
                        .min(Comparator.comparing(FlightOption::arrivalAt))
                        .ifPresent(second -> result.add(new ConnectingOption(
                                hub.get(),
                                first,
                                second,
                                formatDuration((int) Duration.between(first.arrivalAt(), second.departureAt()).toMinutes()),
                                // All airports are on IST, so local date-times can be subtracted directly.
                                formatDuration((int) Duration.between(first.departureAt(), second.arrivalAt()).toMinutes()))));
            }
        }

        result.sort(Comparator
                .comparing((ConnectingOption c) -> c.second().arrivalAt())
                .thenComparing(c -> c.first().departureAt()));
        return result.size() > MAX_CONNECTIONS ? List.copyOf(result.subList(0, MAX_CONNECTIONS)) : result;
    }

    public Optional<FlightOption> findById(long flightInstanceId) {
        List<FlightRow> rows = jdbc.sql(FLIGHT_SELECT + " WHERE fi.flight_instance_id = :id")
                .param("id", flightInstanceId)
                .query(FlightSearchService::mapRow)
                .list();
        return withFares(rows).stream().findFirst();
    }

    private static FlightRow mapRow(ResultSet rs, int n) throws SQLException {
        long instanceId = rs.getLong("flight_instance_id");
        Long id = rs.wasNull() ? null : instanceId;
        return new FlightRow(
                id,
                rs.getString("flight_number"),
                new AirportInfo(rs.getString("o_code").trim(), rs.getString("o_name"), rs.getString("o_city")),
                new AirportInfo(rs.getString("d_code").trim(), rs.getString("d_name"), rs.getString("d_city")),
                rs.getObject("dep_local", LocalDateTime.class),
                rs.getObject("arr_local", LocalDateTime.class),
                rs.getInt("dur_min"),
                rs.getString("status"),
                rs.getString("terminal"),
                rs.getString("aircraft"));
    }

    private List<FlightOption> withFares(List<FlightRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(FlightRow::id).filter(java.util.Objects::nonNull).toList();

        // price per passenger = base_fare * fare_multiplier * (1 + tax% / 100)
        record FareRow(long flightInstanceId, CabinFare fare, BigDecimal price) {}
        List<FareRow> fareRows = ids.isEmpty() ? List.<FareRow>of() : jdbc.sql("""
                        SELECT ff.flight_instance_id,
                               ff.cabin_class,
                               ff.base_fare * ff.fare_multiplier * (1 + ff.tax_percent / 100) AS price,
                               GREATEST(inv.total_seats - inv.held_seats - inv.confirmed_seats - inv.blocked_seats, 0) AS seats_left
                        FROM flight_fare ff
                        JOIN flight_instance_cabin_inventory inv
                          ON inv.flight_instance_id = ff.flight_instance_id
                         AND inv.cabin_class = ff.cabin_class
                        WHERE ff.flight_instance_id IN (:ids)
                        """)
                .param("ids", ids)
                .query((rs, n) -> {
                    String cabin = rs.getString("cabin_class");
                    BigDecimal price = rs.getBigDecimal("price").setScale(0, RoundingMode.HALF_UP);
                    return new FareRow(
                            rs.getLong("flight_instance_id"),
                            new CabinFare(cabin, CABIN_LABELS.getOrDefault(cabin, cabin), formatPrice(price), rs.getInt("seats_left"), price),
                            price);
                })
                .list();

        Map<Long, List<FareRow>> byFlight = new LinkedHashMap<>();
        for (FareRow fr : fareRows) {
            byFlight.computeIfAbsent(fr.flightInstanceId(), k -> new ArrayList<>()).add(fr);
        }

        List<FlightOption> result = new ArrayList<>();
        for (FlightRow row : rows) {
            List<FareRow> mine = new ArrayList<>(byFlight.getOrDefault(row.id(), List.of()));
            mine.sort((a, b) -> Integer.compare(
                    CABIN_ORDER.getOrDefault(a.fare().code(), 99),
                    CABIN_ORDER.getOrDefault(b.fare().code(), 99)));

            String lowest = mine.stream()
                    .filter(f -> f.fare().isAvailable())
                    .map(FareRow::price)
                    .min(BigDecimal::compareTo)
                    .map(this::formatPrice)
                    .orElse(null);

            long dayDiff = java.time.temporal.ChronoUnit.DAYS.between(row.dep().toLocalDate(), row.arr().toLocalDate());

            result.add(new FlightOption(
                    row.id(),
                    row.number(),
                    row.origin(),
                    row.destination(),
                    row.dep().format(TIME),
                    row.dep().format(DAY),
                    row.arr().format(TIME),
                    dayDiff > 0 ? "+" + dayDiff + (dayDiff == 1 ? " day" : " days") : null,
                    formatDuration(row.durationMin()),
                    row.status(),
                    row.aircraft(),
                    row.terminal(),
                    mine.stream().map(FareRow::fare).toList(),
                    lowest,
                    row.dep(),
                    row.arr()));
        }
        return result;
    }

    public String formatPrice(BigDecimal price) {
        return "₹" + rupees.format(price);
    }

    private static String formatDuration(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + String.format("%02d", m) + "m";
    }
}

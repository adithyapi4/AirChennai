-- AirChennai Flyway migration V18
-- DEMO / SEED DATA so that flight search returns results out of the box.
-- Creates: 3 aircraft types (+ seat layouts), directional routes between 14 major
-- airports, recurring daily flights, one flight_instance per flight per day
-- (2026-10-19 .. 2027-03-31), and fares for every cabin that has seats.
-- This is NOT a real timetable. Remove this migration (or replace it with real
-- schedule data) before going to production.

------------------------------------------------------------------------------
-- 1. Aircraft types
------------------------------------------------------------------------------
-- Cabin sizes are chosen so seat_layout can be generated exactly:
--   BUSINESS      = 2-2 layout (4 seats / row)
--   PREMIUM ECON. = 3-3 layout (6 seats / row)
--   ECONOMY       = 3-3 layout (6 seats / row)
INSERT INTO aircraft_type
    (manufacturer, model, max_range_km, total_seats,
     economy_seats, premium_economy_seats, business_seats, first_seats)
VALUES
    ('Boeing', '737-800',   5400, 164, 156,  0,  8, 0),
    ('Airbus', 'A320-200',  6150, 180, 168,  0, 12, 0),
    ('Airbus', 'A321-200',  7400, 202, 162, 24, 16, 0)
ON CONFLICT (manufacturer, model) DO NOTHING;

-- Physical seat blueprint (checked by the deferred triggers in V5 at commit).
INSERT INTO seat_layout
    (aircraft_type_id, row_number, seat_letter, column_position, cabin_class, seat_position)
WITH cfg AS (
    SELECT aircraft_type_id AS id,
           business_seats / 4         AS biz_rows,
           premium_economy_seats / 6  AS pe_rows,
           economy_seats / 6          AS eco_rows
    FROM aircraft_type
    WHERE (manufacturer, model) IN (('Boeing', '737-800'), ('Airbus', 'A320-200'), ('Airbus', 'A321-200'))
),
seat_rows AS (
    SELECT id, r AS row_number, 'BUSINESS'::text AS cabin_class, 'ACDF'::text AS letters
      FROM cfg, generate_series(1, biz_rows) AS r
    UNION ALL
    SELECT id, r, 'PREMIUM_ECONOMY', 'ABCDEF'
      FROM cfg, generate_series(biz_rows + 1, biz_rows + pe_rows) AS r
    UNION ALL
    SELECT id, r, 'ECONOMY', 'ABCDEF'
      FROM cfg, generate_series(biz_rows + pe_rows + 1, biz_rows + pe_rows + eco_rows) AS r
)
SELECT sr.id,
       sr.row_number::smallint,
       substr(sr.letters, col, 1)::char(1),
       col::smallint,
       sr.cabin_class,
       CASE WHEN substr(sr.letters, col, 1) IN ('A', 'F') THEN 'WINDOW'
            WHEN substr(sr.letters, col, 1) IN ('B', 'E') THEN 'MIDDLE'
            ELSE 'AISLE' END
FROM seat_rows sr
CROSS JOIN LATERAL generate_series(1, length(sr.letters)) AS col
ON CONFLICT DO NOTHING;

------------------------------------------------------------------------------
-- 2. City pairs (undirected) with approximate distance and daily frequency
------------------------------------------------------------------------------
CREATE TEMP TABLE seed_pair (
    pair_no INTEGER,
    a CHAR(3),
    b CHAR(3),
    km INTEGER,
    freq INTEGER
) ON COMMIT DROP;

INSERT INTO seed_pair (pair_no, a, b, km, freq)
SELECT row_number() OVER (), a, b, km, freq
FROM (VALUES
    ('MAA', 'BOM', 1030, 4),
    ('MAA', 'DEL', 1760, 4),
    ('MAA', 'BLR',  290, 4),
    ('MAA', 'HYD',  520, 3),
    ('MAA', 'CCU', 1370, 3),
    ('MAA', 'COK',  680, 3),
    ('MAA', 'IXM',  380, 2),
    ('MAA', 'CJB',  380, 2),
    ('MAA', 'TRV',  730, 2),
    ('MAA', 'VTZ',  610, 2),
    ('MAA', 'GOI',  800, 2),
    ('MAA', 'PNQ', 1030, 2),
    ('MAA', 'AMD', 1370, 1),
    ('BOM', 'DEL', 1140, 5),
    ('BOM', 'BLR',  840, 4),
    ('BOM', 'HYD',  620, 3),
    ('BOM', 'CCU', 1650, 3),
    ('BOM', 'GOI',  440, 3),
    ('BOM', 'COK', 1100, 2),
    ('BOM', 'AMD',  440, 3),
    ('DEL', 'BLR', 1740, 4),
    ('DEL', 'HYD', 1260, 3),
    ('DEL', 'CCU', 1305, 4),
    ('BLR', 'HYD',  500, 3),
    ('BLR', 'COK',  360, 2),
    ('BLR', 'GOI',  440, 2),
    ('HYD', 'CCU', 1180, 2)
) AS p(a, b, km, freq);

------------------------------------------------------------------------------
-- 3. Routes (both directions)
------------------------------------------------------------------------------
INSERT INTO route (origin_code, destination_code, distance_km)
SELECT a, b, km FROM seed_pair
UNION ALL
SELECT b, a, km FROM seed_pair
ON CONFLICT (origin_code, destination_code) DO NOTHING;

------------------------------------------------------------------------------
-- 4. Recurring flights (every day of the week)
------------------------------------------------------------------------------
INSERT INTO flight
    (flight_number, route_id, aircraft_type_id,
     departure_time_local, arrival_time_local, arrival_day_offset, days_of_week)
WITH dirs AS (
    SELECT a AS o, b AS d, km, freq, pair_no, 0 AS dir FROM seed_pair
    UNION ALL
    SELECT b, a, km, freq, pair_no, 1 FROM seed_pair
),
slots AS (
    SELECT t, n
    FROM unnest(ARRAY['06:10', '08:55', '12:30', '16:20', '19:45']::time[])
         WITH ORDINALITY AS s(t, n)
),
sched AS (
    SELECT dirs.o, dirs.d, dirs.km,
           (s.t + dirs.dir * interval '20 minutes')::time AS dep,
           (5 * round((40 + dirs.km / 13.0) / 5))::int     AS dur_min
    FROM dirs
    CROSS JOIN LATERAL generate_series(1, dirs.freq) AS k
    JOIN slots s ON s.n = ((k - 1) * 5 / dirs.freq + dirs.pair_no) % 5 + 1
),
numbered AS (
    SELECT sched.*,
           row_number() OVER (ORDER BY o, d, dep) AS rn
    FROM sched
)
SELECT 'AC' || (100 + n.rn),
       r.route_id,
       at.aircraft_type_id,
       n.dep,
       (n.dep + n.dur_min * interval '1 minute')::time,
       CASE WHEN extract(epoch FROM n.dep) / 60 + n.dur_min >= 1440 THEN 1 ELSE 0 END,
       127
FROM numbered n
JOIN route r
  ON r.origin_code = n.o AND r.destination_code = n.d
JOIN aircraft_type at
  ON at.model =
     CASE WHEN n.km <= 700  THEN '737-800'
          WHEN n.km <= 1400 THEN 'A320-200'
          ELSE                   'A321-200' END;

------------------------------------------------------------------------------
-- 5. Dated departures. Triggers fill the aircraft type snapshot and the
--    flight_instance_cabin_inventory rows automatically.
------------------------------------------------------------------------------
INSERT INTO flight_instance
    (flight_id, flight_date, aircraft_type_id,
     scheduled_departure_at, scheduled_arrival_at,
     estimated_departure_at, estimated_arrival_at, status)
SELECT f.flight_id,
       d::date,
       f.aircraft_type_id,
       (d::date + f.departure_time_local) AT TIME ZONE 'Asia/Kolkata',
       (d::date + f.arrival_day_offset + f.arrival_time_local) AT TIME ZONE 'Asia/Kolkata',
       (d::date + f.departure_time_local) AT TIME ZONE 'Asia/Kolkata',
       (d::date + f.arrival_day_offset + f.arrival_time_local) AT TIME ZONE 'Asia/Kolkata',
       'SCHEDULED'
FROM flight f
CROSS JOIN generate_series(date '2026-10-19', date '2027-03-31', interval '1 day') AS d
WHERE (f.days_of_week & (1 << (extract(isodow FROM d)::int - 1))) <> 0
ON CONFLICT (flight_id, flight_date) DO NOTHING;

------------------------------------------------------------------------------
-- 6. Fares. Economy scales with distance; premium economy = 1.6x, business = 3.2x.
--    Friday/Sunday carry a 1.15 demand multiplier.
--    Price shown to a customer = base_fare * fare_multiplier * (1 + tax_percent/100).
------------------------------------------------------------------------------
INSERT INTO flight_fare
    (flight_instance_id, cabin_class, base_fare, fare_multiplier, tax_percent)
SELECT fi.flight_instance_id,
       c.cabin_class,
       round((1700 + r.distance_km * 3.4) * c.factor, -1),
       CASE WHEN extract(isodow FROM fi.flight_date) IN (5, 7) THEN 1.15 ELSE 1.00 END,
       c.tax_percent
FROM flight_instance fi
JOIN flight f ON f.flight_id = fi.flight_id
JOIN route r  ON r.route_id  = f.route_id
JOIN aircraft_type at ON at.aircraft_type_id = fi.aircraft_type_id
CROSS JOIN LATERAL (VALUES
    ('ECONOMY'::varchar,         1.0, 5.0, at.economy_seats),
    ('PREMIUM_ECONOMY'::varchar, 1.6, 5.0, at.premium_economy_seats),
    ('BUSINESS'::varchar,        3.2, 12.0, at.business_seats)
) AS c(cabin_class, factor, tax_percent, seats)
WHERE c.seats > 0
ON CONFLICT (flight_instance_id, cabin_class) DO NOTHING;

# AirChennai – API Documentation

Base URL (local): `http://localhost:8080`

AirChennai has **one JSON REST endpoint** (airport autocomplete). Everything else is a server-rendered
Thymeleaf page (HTML) reached with `GET` and submitted with `POST` forms. Both kinds are listed so the
document matches every route in the code.

**Conventions**
- Dates are ISO-8601 (`2026-11-10`); times are shown in IST for Indian airports.
- Airport codes are 3-letter IATA codes, case-insensitive on input.
- Invalid or tampered query strings on customer pages do not return 4xx; the controller redirects (`302`) to `/`.
- All `POST` forms are CSRF-protected (Spring Security default) – browsers get the token from the Thymeleaf form; API clients must send it.
- Access: **Public** = no login intended; **Login** = any signed-in employee; **HR** = role `HR` (`ROLE_HR`).


---

## 1. JSON API

### GET `/api/airports` — airport autocomplete
Access: Public\* · Controller: `AirportController`

| | |
|---|---|
| Query parameter | `q` (string, optional, default empty) – matched against IATA code (prefix), city (prefix) and airport name (contains), case-insensitive |
| Request body | none |
| Success | `200 OK`, `application/json`, array of at most **8** airports; only `active` airports |
| Empty / blank `q` | `200 OK`, `[]` |
| Ordering | exact code match → code prefix → city prefix → name/city contains, then city, name |
| Notes | `%` and `_` in `q` are matched literally |

Response item:

| Field | Type | Example |
|---|---|---|
| `code` | string (3) | `"MAA"` |
| `name` | string | `"Chennai International Airport"` |
| `city` | string | `"Chennai"` |
| `country` | string | `"India"` |

```bash
curl "http://localhost:8080/api/airports?q=chen"
```
```json
[{"code":"MAA","name":"Chennai International Airport","city":"Chennai","country":"India"}]
```

---

## 2. Customer web flow (HTML)

| Method | URL | Access | Parameters | Result |
|---|---|---|---|---|
| GET | `/` | Public\* | – | Home page with the search box (`index`) |
| GET | `/flights/search` | Public\* | `from`, `to` (IATA), `departureDate` (ISO), `tripType` = `ONE_WAY`(default) \| `ROUND_TRIP`, `returnDate` (needed for round trip) | `200` results page (`search-results`): direct flights; if none, one-stop connections. `302 /` if airport unknown, same origin/destination, bad date, or return before departure |
| GET | `/booking/new` | Public\* | `flightInstanceId` (long), `cabin` (`ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS`, `FIRST`) | `200` booking summary (`booking-start`); `302 /` if the flight or an available fare for that cabin does not exist |
| GET | `/booking/seats` | Public\* | `flightInstanceId`, `cabin` (default `ECONOMY`) | `200` seat map (`seat-selection`); `302 /` if the flight is not bookable (must be `SCHEDULED`/`DELAYED` and in the future) |
| POST | `/booking/seats` | Public\* | form: `flightInstanceId`, `cabin`, `seat` (e.g. `12A`) | Holds the seat for **10 minutes** for this browser session, `302 /booking/payment?flightInstanceId=…`. If the seat was taken: `302` back to the seat map with an error message |
| GET | `/booking/payment` | Public\* | `flightInstanceId` | `200` payment summary (`payment`) with fare + seat fee. `302` to the seat map with "hold expired" if no active hold |

Typical sequence: `/` → `/flights/search` → `/booking/new` → `/booking/seats` (GET, then POST) → `/booking/payment`.

---

## 3. Employee portal (HTML)

### Authentication
| Method | URL | Access | Parameters | Result |
|---|---|---|---|---|
| GET | `/employee/login` | Public | – | Login page |
| POST | `/employee/login` | Public | `employeeId` (employee code), `password` | Success → dashboard by department; if `must_change_password` → `/employee/change-password`. Failure → `/employee/login?error` |
| POST | `/employee/logout` | Login | – | Session invalidated → `/employee/login?logout` |
| GET | `/employee/change-password` | Login | – | Change-password form |
| POST | `/employee/change-password` | Login | `currentPassword`, `newPassword`, `confirmPassword` | Password rules: ≥ 12 chars with upper, lower, digit, special; must differ from current. On success the user is logged out and must sign in again; on failure the form is shown again with a message |

Dashboard destinations after login: `ADMIN → /employee/admin/dashboard`, `HR → /employee/hr/dashboard`,
`COUNTER → /employee/counter/dashboard`, `OPERATIONS → /employee/operations/dashboard`,
`FLEET → /employee/fleet/dashboard`. **Only the HR dashboard exists in code today**; the others currently return `404`.

### HR (role `HR`)
| Method | URL | Parameters | Result |
|---|---|---|---|
| GET | `/employee/hr/dashboard` | – | HR dashboard |
| GET | `/employee/hr/employees/add` | – | Add-employee form |
| POST | `/employee/hr/employees/add` | `employeeCode` (3–20 of `A-Z 0-9 _ -`), `email`, `firstName`, `lastName`, `phoneNo` (10-digit Indian mobile, starts 6–9), `department` ∈ `HR`,`COUNTER`,`OPERATIONS`,`FLEET` | Creates an `ACTIVE` employee with a generated 16-char temporary password, shown **once** after redirect; user must change it at first login. Validation errors or duplicate code/email re-display the form |
| GET | `/employee/hr/employees/remove` | – | Remove-employee page |
| POST | `/employee/hr/employees/remove/lookup` | `employeeCode` | Shows the employee and whether removal is allowed (not yourself, not ADMIN, not already terminated) |
| POST | `/employee/hr/employees/remove/confirm` | `employeeCode` | Soft delete: sets `status = 'TERMINATED'` |
| GET | `/employee/hr/airports` | – | Change-base-airport page |
| POST | `/employee/hr/airports/lookup` | `employeeCode` | Shows the employee and current base airport |
| POST | `/employee/hr/airports/preview` | `employeeCode`, `airportCode` | Previews the change; rejects unknown/inactive airports and unchanged airport |
| POST | `/employee/hr/airports/change` | `employeeCode`, `airportCode` | Updates `base_airport_code` |

### Fleet
| Method | URL | Access | Parameters | Result |
|---|---|---|---|---|
| GET | `/employee/fleet/aircraft-types/add` | Login | – | Aircraft-type form |
| POST | `/employee/fleet/aircraft-types/add` | Login | `manufacturer`, `model`, `maxRangeKm`, `totalSeats`, and for each cabin `economy`, `premiumEconomy`, `business`, `first`: `rows`, `blockCount`, `seatsPerBlock[0..3]` | Inserts the aircraft type and generates its `seat_layout` rows in one transaction. Limits: 4 seat blocks, 200 rows per cabin, 26 seats per block, 700 seats total. Duplicate manufacturer+model or invalid layout re-displays the form with errors |

---

## 4. Status-code summary

| Situation | Behaviour |
|---|---|
| Successful page | `200` |
| Successful form submit | `302` redirect (Post/Redirect/Get) |
| Not signed in on a protected URL | `302 /employee/login` |
| Signed in but wrong role | `403` |
| Must change password | every URL except change-password, logout and `/error` → `302 /employee/change-password` |
| Unknown path / no dashboard | `404` |

# AirChennai – Airline Management System

AirChennai is a Spring Boot web application for an Indian domestic airline. Customers search flights
(direct and one-stop), choose a seat and move to payment; airline staff log in to an employee portal
where HR manages employees and Fleet defines aircraft types and seat layouts.

> **Status:** work in progress. The sections below describe what is implemented *today*. Planned
> features are listed under [Roadmap](#roadmap).

## Features

**Customer side (no account needed – guest booking)**
- Airport autocomplete (`GET /api/airports?q=`)
- Flight search by origin, destination and date; one-way or round trip
- One-stop connection search when no direct flight exists (layover 1–10 h)
- Interactive seat map with a 10-minute seat hold, then a payment page

**Employee side (login required, role = department)**
- Login with employee code, BCrypt-hashed passwords, forced password change on first login
- Initial `ADMIN` account created automatically on first start
- HR: add employee (temporary password generated), remove (soft-terminate) employee, change an employee's base airport
- Fleet: add aircraft type with a generated seat layout

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Security, Bean Validation) |
| View | Thymeleaf server-side templates |
| Data access | Spring `JdbcTemplate` / `JdbcClient` (Spring Data JPA starter is on the classpath; Hibernate validates the schema) |
| Database | PostgreSQL |
| Migrations | Flyway (`src/main/resources/db`, V1 – V19) |
| Build | Maven (wrapper included) |

## Prerequisites

- JDK 25 (`java -version`)
- PostgreSQL reachable on **localhost:5433** (change `spring.datasource.url` in `application.properties` if yours differs)
- Nothing else – Maven is provided by `./mvnw`

## Database setup

Create the database and two roles: a **migration** role that owns the schema (Flyway) and an **application** role with data-only rights. Example:

```sql
CREATE ROLE airchennai_migrator LOGIN PASSWORD 'change-me-1';
CREATE ROLE airchennai_app      LOGIN PASSWORD 'change-me-2';
CREATE DATABASE airchennai OWNER airchennai_migrator;

\c airchennai
GRANT USAGE ON SCHEMA public TO airchennai_app;
ALTER DEFAULT PRIVILEGES FOR ROLE airchennai_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO airchennai_app;
ALTER DEFAULT PRIVILEGES FOR ROLE airchennai_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO airchennai_app;
```

Tables are created automatically on first start by Flyway. V1 seeds Indian airports and V18 seeds
**demo flights for 2026-10-19 to 2027-03-31** (daily flights between 14 major airports) – search with a
date inside that window.

## Configuration

Set these environment variables before starting (see `src/main/resources/application.properties`):

| Variable | Meaning |
|---|---|
| `DB_USER` / `DB_PASSWORD` | Application database role |
| `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD` | Flyway migration role |
| `AIRCHENNAI_ADMIN_INITIAL_PASSWORD` | **Required on first start**, ≥ 12 characters. Used to create the first ADMIN account |
| `AIRCHENNAI_ADMIN_EMPLOYEE_CODE` | Optional, default `ADMIN001` |
| `AIRCHENNAI_ADMIN_EMAIL`, `_FIRST_NAME`, `_LAST_NAME`, `_PHONE` | Optional admin profile fields |

Linux / macOS:
```bash
export DB_USER=airchennai_app DB_PASSWORD=change-me-2
export DB_MIGRATION_USER=airchennai_migrator DB_MIGRATION_PASSWORD=change-me-1
export AIRCHENNAI_ADMIN_INITIAL_PASSWORD='A-long-initial-pass-1!'
```
Windows PowerShell: `$env:DB_USER="airchennai_app"` (same pattern for the others). In IntelliJ, put them in the run configuration's *Environment variables*.

## Build and run

```bash
git clone https://github.com/adithyapi4/AirChennai.git
cd AirChennai
./mvnw clean package -DskipTests   # build
./mvnw spring-boot:run             # run  -> http://localhost:8080
```

The application stops at start-up with a clear message if no ADMIN exists and
`AIRCHENNAI_ADMIN_INITIAL_PASSWORD` is missing or shorter than 12 characters.

### First login
1. Open `http://localhost:8080/employee/login`
2. Employee code `ADMIN001` (or your override) and the initial password
3. You are redirected to *change password* – choose a new password (≥ 12 chars, upper, lower, digit, special)
4. Further employees are created from the HR portal (`/employee/hr/employees/add`), which only the `HR` role can open. The ADMIN account has no access to `/employee/hr/**` today, so the first HR employee must be inserted directly in the database (see *Known limitations* in [`docs/DESIGN.md`](docs/DESIGN.md))

## Sample requests

```bash
# Airport autocomplete (JSON)
curl "http://localhost:8080/api/airports?q=chen"
# -> [{"code":"MAA","name":"Chennai International Airport","city":"Chennai","country":"India"}]

# Flight search (HTML page) – Chennai to Mumbai, one way
curl "http://localhost:8080/flights/search?from=MAA&to=BOM&departureDate=2026-11-10"

# Round trip
curl "http://localhost:8080/flights/search?from=MAA&to=DEL&departureDate=2026-11-10&tripType=ROUND_TRIP&returnDate=2026-11-14"
```
Full endpoint reference: [`docs/API.md`](docs/API.md).

## Project structure

```
src/main/java/com/aa/airchennai
├── AirChennaiApplication.java      entry point
├── airport/                        airport autocomplete REST endpoint
├── flight/                         flight + connection search (controller, service)
├── booking/                        booking start, seat map, seat hold, payment page
├── employee/
│   ├── auth/                       security config, login, password change, admin bootstrap
│   ├── hr/                         add / remove employee, change base airport
│   └── fleet/                      add aircraft type + seat layout
└── web/                            home page
src/main/resources
├── db/                             Flyway migrations V1 … V19
└── templates/                      Thymeleaf views
```
Design report (architecture, ER diagram, request flow): [`docs/DESIGN.md`](docs/DESIGN.md).

## Tests

```bash
./mvnw test
```
Requires the database and environment variables above (the current test loads the full application context).

## Roadmap

Planned, not yet implemented: payment and PNR/ticket generation, web check-in, cancellations and
modifications, ticket download / e-mail, frequent-flyer programme, fleet scheduling and tracking,
adding routes and flights from the employee portal, baggage mapping at check-in.

## Team

Adithya ([@adithyapi4](https://github.com/adithyapi4)) – employee / admin side · teammate – customer side

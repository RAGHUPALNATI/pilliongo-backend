# PillionGo

[![Backend CI](https://github.com/RAGHUPALNATI/pilliongo-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/RAGHUPALNATI/pilliongo-backend/actions/workflows/ci.yml)

PillionGo is a full-stack, peer-to-peer ride-sharing web app for anyone, anywhere.
It connects riders who need a lift with drivers already heading the same way,
for both "right now" instant rides and pre-planned scheduled trips, so people
can share bikes and cars and split the cost of the journey.

It replaces the informal carpooling people already do through random chat
groups with real accounts, a public board of ride offers, upfront fare
estimates, multi-seat bookings, and a basic safety layer: verified emails,
visible driver and vehicle details, live location during a ride, an SOS
button and admin oversight.

## Why this exists

Every day, people travel the same routes alone, while others on those exact
routes are looking for a ride, whether that's to the bus stand, the railway
station, work, college or the next town. Lifts get arranged informally and
unsafely (hitchhiking, unverified strangers). PillionGo gives that same
ride-sharing a proper account system, a public planned-rides board, and a way
for a rider to see who's driving them (name, phone, vehicle) before they get in.

The app ships with a starter set of locations and fare zones (around
Phagwara and Jalandhar, Punjab, where it was first built and tested). Admins
can add new locations and fares from the admin panel, so it works for any
city or region.

## Tech stack

| Layer | Technology | Version |
|---|---|---|
| Backend framework | Spring Boot | 4.1.1 |
| Backend language | Java | 21 |
| Build tool | Maven | — |
| Database | MySQL | 8.x |
| Auth | JWT (`io.jsonwebtoken` / jjwt) | 0.13.0 |
| Password hashing | Spring Security `BCryptPasswordEncoder` | — |
| Frontend framework | Next.js (App Router) | 14.2.5 |
| Frontend styling | Tailwind CSS | 3.4.4 |
| HTTP client | Axios | — |

## Project structure

```
pilliongo/                     backend — Spring Boot / Maven project
  src/main/java/com/raghu/pilliongo/
    controller/                REST controllers (auth, rides, driver, admin)
    service/                   business logic
    model/                     JPA entities
    dto/                       request/response DTOs (Bean Validation lives here)
    repository/                Spring Data JPA repositories
    security/                  JWT filter, JwtUtil, SecurityConfig
    util/                      FareCalculator (shared distance/fare logic)
    exception/                 GlobalExceptionHandler
  src/main/resources/
    application.properties

pilliongo-frontend/            frontend — Next.js App Router project
  app/                         pages (rider, driver, admin dashboards, auth, etc.)
  components/                  shared UI components
  lib/api.js                   single source of truth for all backend API calls
```

## Setup instructions

### 1. Database

Create a MySQL database named `pilliongo` (the app auto-creates/updates
tables via `spring.jpa.hibernate.ddl-auto=update`, so no manual schema
scripts are needed):

```sql
CREATE DATABASE pilliongo;
```

### 2. Secrets (.env)

No secret is stored in `application.properties`. Copy `.env.example` to
`.env` in the backend folder (next to `pom.xml`) and fill in real values —
Spring Boot loads it at startup via
`spring.config.import=optional:file:.env[.properties]`. `.env` is
git-ignored. Real OS/IDE environment variables with the same names override it.

| Variable | Used for |
|---|---|
| `DB_PASSWORD` | MySQL password |
| `JWT_SECRET` | Signs/verifies JWT auth tokens (use 32+ random chars) |
| `MAIL_PASSWORD` | Brevo **SMTP key** for sending OTP emails |

If any of these is missing, the backend fails at startup with a
"Could not resolve placeholder" error — that's deliberate.

### 3. Run the backend

```bash
cd pilliongo
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080` by default (`server.port` in
`application.properties`).

> The frontend defaults to `http://localhost:8080/api`; set
> `NEXT_PUBLIC_API_URL` in the frontend's `.env.local` if you change the port.

### 4. Run the frontend

```bash
cd pilliongo-frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:3000`.

### 5. Create an admin account

There's no public admin registration (the register endpoint blocks
`role: "ADMIN"` on purpose). Insert one directly, or temporarily register as
a normal role and update it in the database:

```sql
UPDATE users SET role = 'ADMIN', email_verified = true WHERE email = 'you@example.com';
```

## Deploying

Everything environment-specific is a setting, so the same code runs on your
laptop and on a host. Set these environment variables on the host:

| Variable | Example | What it is |
|---|---|---|
| `DB_URL` | `jdbc:mysql://<host>:4000/pilliongo?sslMode=VERIFY_IDENTITY` | Database address (MySQL, or MySQL-compatible TiDB Cloud) |
| `DB_USERNAME` | `xxxx.root` | Database user |
| `DB_PASSWORD` | | Database password |
| `DB_POOL_SIZE` | `5` | Open connections (keep small on free database plans) |
| `JWT_SECRET` | | Long random string |
| `MAIL_PASSWORD` | | Brevo SMTP key |
| `CORS_ALLOWED_ORIGINS` | `https://pilliongo.vercel.app` | Your frontend's address(es), comma-separated |

`PORT` is read automatically if the host provides it. Tables and indexes
are created on first start (`ddl-auto=update`).

On the frontend host, set `NEXT_PUBLIC_API_URL` to `https://<your-backend>/api`.

**Built to stay cheap under load:** the screens refresh every 10-15 seconds
(and not at all in a background tab), the hot queries fetch only the rows
they need, and those lookups are indexed.

## Multi-seat carpooling

A driver offering a pre-planned car ride sets how many seats they have
(1–6; a bike is always 1). Riders can book one or more of those seats:

- Each rider gets their **own booking** (a ride row with `parentOfferId`
  pointing at the offer), so start / complete / cancel / mark-paid /
  history all work per passenger, and the driver is credited per booking.
- The offer stays on the board until every seat is taken, then hides.
- A rider cancelling gives their seats back to the offer; a driver
  cancelling the whole offer cancels every booking on it and notifies each rider.
- Booking fare = per-seat fare × seats booked.
- Single-seat offers (all bikes, all instant offers) behave exactly as
  before — the offer itself becomes the booking.

## Testing

**1. Unit tests** (no database, no server, a few seconds):

```bash
./mvnw test -Dtest=RideServiceTest
```

Covers seat rules, booking, double-booking, cancelling and the ride lifecycle.
(`PilliongoApplicationTests` boots the whole app, so it needs MySQL and `.env`.)

**2. API failure checks** (backend must be running on :8080):

```powershell
powershell -ExecutionPolicy Bypass -File tests\api-checks.ps1
```

Hits the live API with bad input, missing/fake tokens, wrong roles, broken
JSON, overbooking, etc., and prints PASS/FAIL for each. Part B asks for a
verified rider + driver login and cleans up the test ride it creates.

**Error contract** — every error is JSON `{"message": "..."}` with:
400 bad input / broken rule · 401 not logged in or session expired ·
403 not allowed · 404 not found · 409 someone else changed it first ·
503 email service down · 500 only for real bugs (details go to the log, not the client).

## API endpoints

All endpoints are prefixed with `/api`. Endpoints marked **Auth** require an
`Authorization: Bearer <token>` header from `/auth/login`.

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Register as RIDER or DRIVER |
| POST | `/auth/verify-otp` | Public | Verify the emailed OTP |
| POST | `/auth/login` | Public | Login, returns a JWT |
| POST | `/auth/resend-otp` | Public | Resend a new OTP |
| PUT | `/auth/profile` | Auth | Update own profile (partial update) |
| POST | `/auth/forgot-password` | Public | Email a password-reset OTP |
| POST | `/auth/verify-reset-otp` | Public | Exchange the OTP for a short-lived reset token |
| POST | `/auth/reset-password` | Public | Set a new password with the reset token |
| POST | `/rides` | Auth (rider) | Create a ride request (INSTANT or PLANNED) |
| POST | `/rides/offer` | Auth (driver) | Publish a pre-planned route offer (`seats`: 1–6, bikes always 1) |
| POST | `/rides/offer-instant` | Auth (driver) | Publish an instant "driving now" offer |
| GET | `/rides/instant-offers` | Auth | Browse live instant offers from drivers |
| PUT | `/rides/{id}/book?seats=N` | Auth (rider) | Book N seats on a driver's offer (default 1) |
| GET | `/rides/available` | Auth (driver) | List available rider-posted ride requests |
| GET | `/rides/planned` | Public | Planned-rides bulletin board |
| PUT | `/rides/{id}/accept` | Auth (driver) | Accept a rider's ride request |
| PUT | `/rides/{id}/start` | Auth (rider or driver on that ride) | Mark a ride as started |
| PUT | `/rides/{id}/complete` | Auth (rider or driver on that ride) | Mark a ride as completed |
| DELETE | `/rides/{id}` | Auth (rider or driver on that ride) | Cancel a ride |
| GET | `/rides/history` | Auth | Your own ride history |
| GET | `/rides/{id}` | Auth | Get a single ride's details |
| PUT | `/driver/availability` | Auth (driver) | Toggle available/unavailable |
| GET | `/driver/earnings` | Auth (driver) | Get total earnings |
| GET | `/admin/users` | Auth (admin) | List all users |
| GET | `/admin/users/{id}` | Auth (admin) | Get a single user |
| PUT | `/admin/users/{id}/suspend` | Auth (admin) | Suspend a user |
| PUT | `/admin/users/{id}/activate` | Auth (admin) | Reactivate a suspended user |
| GET | `/admin/rides` | Auth (admin) | List all rides |
| PUT | `/admin/rides/{id}/status` | Auth (admin) | Force a ride to COMPLETED or CANCELLED |
| GET | `/admin/destinations` | Auth (admin) | List fare-managed destinations |
| POST | `/admin/destinations` | Auth (admin) | Add a destination with a fixed fare |
| PUT | `/admin/destinations/{id}` | Auth (admin) | Update a destination |
| DELETE | `/admin/destinations/{id}` | Auth (admin) | Delete a destination |
| GET | `/admin/analytics` | Auth (admin) | Platform-wide stats |

## Screenshots

_(placeholder — add screenshots of the rider dashboard, driver dashboard, and
admin panel here)_

## Known limitations

- Fares are calculated from an estimated distance table (`FareCalculator.java`),
  not real GPS/mapping data, and don't cover every possible route pair — an
  uncommon route falls back to a flat 5km estimate.
- No payment gateway integration — fares shown in the app are informational
  only; no real money moves through PillionGo itself.
- No live GPS tracking of an in-progress ride.
- Only the booking/cancel logic in `RideService` has unit tests
  (`RideServiceTest`); controllers and the frontend aren't tested yet.
- JWTs don't support refresh tokens, so a session ends fully at expiry and
  requires a full re-login.

## Future improvements

- Real map integration (Google Maps/Mapbox) for accurate distance, live
  tracking, and turn-by-turn routes.
- Payment gateway integration for in-app fare settlement.
- Push/email notifications on ride status changes (accepted, started, etc.).
- Rider ratings and reviews for drivers (and vice versa).
- Broader backend test coverage and frontend (Jest/RTL) tests.
- Refresh tokens for a smoother, longer-lived session experience.
- Richer admin analytics (charts, trends over time).

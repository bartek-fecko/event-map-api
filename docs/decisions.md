# Decisions

Why the project looks the way it does. Each entry records the choice, the reason
and the cost we accepted, so a future change can be judged against the original
constraint instead of taste.

## Platform

**1. API-only repository; the frontend is a separate project.**
The backend is a plain REST service and the Next.js app will live in its own
repo. This keeps the deploy units independent (the API can be restarted without
rebuilding the site), and both halves can pick their own release cadence. Cost:
two repositories, and API changes need a contract step, not a shared type.

**2. Path versioning, `/api/v1`.**
Version in the URL is the only scheme every client and proxy understands without
configuration. A breaking change gets `/api/v2`; additive changes do not bump it.
Cost: no content negotiation for versions, which nobody needed here.

**3. Java 25 LTS, Spring Boot 4.1, Maven wrapper.**
Java 25 is the current LTS with the language level the code is written against
(records, pattern matching, text blocks, `getLast()`). The wrapper pins Maven
3.9.16 so a machine without Maven can build. Cost: the toolchain is young, so
some third-party libraries lag — springdoc needed a Boot 4 line, and JJWT is
configured with the Gson serializer to avoid pulling Jackson 2 into a Jackson 3
application.

**4. One Maven module, package-by-feature.**
A single `event-map-api` artifact. Inside `pl.eventmap`, each feature (`event`,
`venue`, `geo`, `media`, …) owns `domain` / `app` / `api` subpackages. A module
split would add build ceremony without a second deploy unit. Cost: package
discipline is by convention, not enforced by the compiler.

**5. Flyway owns the schema; Hibernate is `ddl-auto: validate`.**
Migrations are reviewable, ordered and replayable on an empty database, which is
what a server deploy needs. `validate` makes an entity/schema mismatch fail the
boot instead of altering a production table. Cost: every entity change needs a
migration in the same commit.

## Data model

**6. `geography` instead of `geometry`.**
Every location column is `geography(Point|MultiPolygon, 4326)`. Distances and
`&&` / `ST_Intersects` then work in metres on the spheroid without casts or a
projection choice. Cost: slightly slower than planar geometry, irrelevant at the
row counts of a city map.

**7. GiST indexes on all spatial columns.**
`venue.location` and `city.boundary` have GiST indexes, so the viewport test is
an index scan even when the table grows. The time dimension is indexed too
(`occurrence_starts_idx`, and a GiST on the generated `tstzrange`).

**8. A venue is a place; an event points at exactly one venue.**
Coordinates live in `venue` (name, address, city, point), events reference it.
The same hall hosting ten events stores its position once, and the map joins
`event → venue` instead of denormalising lat/lon into every row.

**9. An event is a template; dates live in `event_occurrence`.**
A one-off event has exactly one occurrence; a weekly concert has many. Public
lists are ordered by occurrence, not by event, which is what a "what's on"
feed needs. Cost: the public list is occurrence-shaped (`eventId` +
`occurrenceId` per row), and the frontend must dedupe when it wants one card per
event.

**10. Categories are a table, many-to-many, admin-managed.**
Eight reference categories are seeded by migration. A join table allows several
per event without schema changes, and the frontend gets stable slugs and colours
from `GET /categories`. Cost: filtering by category needs an `EXISTS` subquery,
which is still index-friendly.

**11. Users are organizers; there is no separate organizer entity.**
Any registered user can submit; the admin role moderates. This removes an
approval/onboarding flow the product did not ask for. Cost: no organisation
profiles or team accounts yet.

**12. JWT (HS256) with a `typ` claim, BCrypt passwords.**
Stateless auth needs no session store and no sticky routing. Access (15 min) and
refresh (14 d) tokens share the signing key and are separated by a `typ` claim,
so a refresh token presented as a bearer token is rejected — an easy bug to ship
otherwise. BCrypt is the default Spring Security encoder. Cost: no server-side
revocation; a stolen refresh token is valid until it expires. Moving to
asymmetric keys or a token allow-list is a one-class change if that matters.

**13. Moderation is a state machine: `DRAFT → PENDING → PUBLISHED | REJECTED`.**
Public submissions never go live unreviewed, which is the minimum for an
open submission form. Rejections carry a reason the organizer can read. A
rejected event may still be published after a later review. Cost: an extra
click per event; there is no bulk approve yet.

## API

**14. The map endpoint returns either clusters or events, never both.**
`GET /map/events?bbox=…&zoom=…` uses SQL `ST_SnapToGrid` below zoom 12 and
individual points at or above it, with `mode` telling the client which shape it
got. A 500-row cap (`truncated` flags it) bounds the payload for a viewport the
size of Poland. Doing this in PostGIS rather than the client keeps the response
small and the browser loop trivial. Cost: clustering is a fixed grid, not
animated spiderfying; the step is zoom-derived in the service.

**15. JPA for writes, hand-written SQL for the two hot reads.**
Entities, cascades and the state machine are far clearer in JPA. The map query
and the event list are the API contract and are tuned in SQL
(`NamedParameterJdbcTemplate`) so the exact columns, joins and ordering are
visible in the code. Cost: two persistence styles, and the read models are
records mapped by hand.

**16. Errors are RFC 9457 `application/problem+json` with a stable `code`.**
Every failure — including 401/403/429 written from filters — has the same shape:
`type`, `title`, `status`, `detail`, `code`, and `fieldErrors` for validation.
The client switches on `code`, never on prose. Cost: filter-level errors bypass
Spring MVC, so `ProblemResponse` duplicates a little serialisation.

**17. Opaque keyset cursors for public lists, `page`/`size` for admin.**
`(starts_at, occurrence_id)` encoded in base64 keeps the public feed stable while
new events are inserted — offsets would show duplicates. The admin queue is
small and benefits from a total count, so it keeps page numbers. Cost: two
pagination styles, and cursors cannot be constructed by hand (deliberate).

**18. Swagger UI is development-only.**
springdoc is on by default and switched off by the `prod` profile. Cost: the
OpenAPI JSON is also unavailable in production; if a machine-readable contract is
needed there, generate it at build time.

## Operations

**19. Local development: database in Docker, application in IntelliJ.**
The IDE gives breakpoints, hot reload and profiler access; Docker gives a
reproducible PostGIS. `compose.yaml` runs only the database (plus optional
pgAdmin under the `tools` profile). Cost: developers need both Docker and a JDK.

**20. Production: one host, Docker Compose, Caddy.**
PostGIS, the API and Caddy in one file. Caddy gets TLS certificates
automatically, proxies to `app:8080` and serves `/media/*` from the shared volume
without waking the JVM. No Kubernetes: a single instance is the stated scale.
Cost: deployments are per-host and there is no zero-downtime rollout.

**21. Profiles `local` / `prod` / `test`, typed validated properties.**
`AppProperties` is a record tree bound at startup with bean validation; missing
`JWT_SECRET` or `ADMIN_PASSWORD` fails the boot with a readable message instead
of a 500 later. Secrets come from environment variables only. Cost: adding a
property means touching the record.

**22. Actuator exposes `health` only.**
Enough for the container healthcheck and an uptime probe; nothing else is
reachable, so no metrics endpoint leaks internals. Cost: no Prometheus output
until it is explicitly added.

**23. One cover image per event, behind an `ImageStorage` interface.**
Files go to a Docker volume through the local implementation; the event stores a
URL. JPEG/PNG/WebP only, 5 MB cap, UUID filenames. The interface is the seam for
S3-compatible storage later. Cost: no thumbnailing or multiple images yet —
serving a 5 MB original as a card image is acceptable at this scale, not at a
large one.

**24. In-memory fixed-window rate limit on `/auth/**` only.**
Credential endpoints are the brute-force surface; the rest of the API is read
heavy and cheap. A `ConcurrentHashMap` counter per client IP avoids a Redis
dependency for a single-instance deployment. Cost: it resets on restart and does
not work across instances — documented, not hidden.

**25. Docker image: multi-stage, layered, non-root.**
Build in `temurin:25-jdk`, run in `temurin:25-jre` as an unprivileged user.
Spring Boot tools-mode layer extraction puts dependencies in a Docker layer that
changes rarely, so application changes rebuild in seconds. `JarLauncher` is the
entrypoint and `-XX:MaxRAMPercentage=75` lets the JVM size itself to the
container limit. Cost: four `COPY` layers instead of one jar.

**26. Tests use real PostGIS and real HTTP.**
`*IT` classes start one shared `postgis/postgis:17-3.5` container via
Testcontainers, boot the app on a random port and call it with `java.net.http`.
That covers Hibernate's spatial mapping, the generated `tstzrange`, Flyway and
security end to end — the parts most likely to break. Failsafe runs them in
`./mvnw verify`, keeping `./mvnw test` fast. Cost: Docker is required for the
build, and the first run pays for the image pull.

**27. Demo data is a local-profile runner, not a migration.**
`DevDataSeeder` inserts Kraków venues and events only under `local`, guarded by
"venues table is empty". Migrations must stay environment-neutral; seed data
must never reach production. Cost: a fresh local database needs one app start to
look alive.

**28. City boundaries are real OSM outlines, simplified.**
Seven launch cities seeded from Nominatim GeoJSON, simplified with
`ST_SimplifyPreserveTopology(0.0008)` (~80 m) so the migration stays ~31 KB and
readable. `tools/fetch-city-boundaries.ps1` reproduces the pipeline. Cost: the
outline is not authoritative for legal boundaries, only for "is this event in
this city" map framing.

**29. Git is local only for now.**
`git init` plus an initial commit gives history from the first line of code. No
remote is configured and nothing is pushed; adding one later is a single command.

**30. The dev database is published on host port 5433, not 5432.**
A native PostgreSQL install (the common `postgresql-x64-15` Windows service)
usually owns 5432, and Windows lets Docker Desktop bind the same port, so
`localhost:5432` silently reaches the wrong server — symptom
`password authentication failed for user "eventmap"` (28P01) from Flyway.
Publishing on `${DB_PORT:-5433}` removes the ambiguity without touching the
user's own PostgreSQL. Cost: one non-default port to remember; the app's default
`DB_URL` and the README already point at it, and production sets `DB_URL`
explicitly.


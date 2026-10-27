# EventMap API

Backend for an event map. REST API over **PostGIS**: events placed on a map,
filtered per viewport, clustered at low zoom, with user submissions and an admin
moderation queue. The Next.js frontend is a separate project; this repo is API only.

- Java 25 LTS · Spring Boot 4.1 · Maven (wrapper included)
- PostgreSQL 17 + PostGIS 3.5, schema owned by Flyway
- JWT auth, BCrypt, RFC 9457 error bodies
- Docker Compose for local development and for the single-server deployment

---

## Quick start (Windows / PowerShell)

```powershell
# 1. JDK 25 must be the active JVM for Maven
$env:JAVA_HOME = "C:\Users\<you>\.jdks\temurin-25.0.5"

# 2. Database in Docker (PostGIS on localhost:5433)
docker compose up -d db

# 3. Application with the local profile: migrations + demo data
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

macOS / Linux / Git Bash: same commands with `./mvnw` instead of `.\mvnw.cmd`.

Then:

- API: <http://localhost:8080/api/v1/map/events?bbox=19.85,50.02,20.05,50.10&zoom=13>
- Swagger UI (local profile only): <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Demo admin: `admin@eventmap.local` / `admin12345`

## Is it working?

With the app running, one command checks the whole stack end to end:

```powershell
.\tools\check-local.ps1              # health, seed data, map, errors, login, create, upload
.\tools\check-local.ps1 -ReadOnly    # skips the three write checks
```

It prints one line per check and exits `0` when everything passes, `1` when a
check fails, `2` when nothing listens on `http://localhost:8080` (use `-BaseUrl`
to check another host/port). A full run writes one published event named
`Check run <date>` and one 1×1 PNG, so delete the event afterwards if you care
about a tidy dev database.

Manual spot checks:

```powershell
curl.exe -s http://localhost:8080/actuator/health
curl.exe -s "http://localhost:8080/api/v1/map/events?bbox=19.85,50.02,20.05,50.10&zoom=13"   # events
curl.exe -s "http://localhost:8080/api/v1/map/events?bbox=19.85,50.02,20.05,50.10&zoom=8"    # clusters
curl.exe -s http://localhost:8080/api/v1/cities
```

And on the database side (`docker compose ps` should show `eventmap-db` healthy):

```powershell
docker exec eventmap-db psql -U eventmap -d eventmap -c "select version, description, success from flyway_schema_history;"
docker exec eventmap-db psql -U eventmap -d eventmap -c "select status, count(*) from event group by status;"
```

`docker compose stop` keeps the data volume; `docker compose down -v` deletes it
and you get a fresh migrated + seeded database on the next start.

## Opening the project in IntelliJ IDEA

1. **File → Open** and pick `pom.xml`, then **Open as Project**. IntelliJ imports
   the single Maven module `event-map-api`.
2. **File → Project Structure → SDK**: select a JDK 25. IntelliJ auto-detects
   JDKs under `%USERPROFILE%\.jdks`, so a downloaded Temurin 25 appears there.
   Set **Language level** to 25 as well.
3. The shared run configuration **EventMap local** is committed in `.run/`. It
   starts `pl.eventmap.EventMapApplication` with `SPRING_PROFILES_ACTIVE=local`.
   It needs the database from step 2 of the quick start.
4. Run Maven tests from the Maven tool window or `.\mvnw.cmd verify`.

If Maven inside IntelliJ still uses an old JDK, set
**Settings → Build Tools → Maven → Runner → JRE** to the same JDK 25.

## Database

| Setting  | Value       |
| -------- | ----------- |
| Host     | `localhost` |
| Port     | `5433`      |
| Database | `eventmap`  |
| User     | `eventmap`  |
| Password | `eventmap`  |

- **IntelliJ Database tool** (Ultimate): **+ → Data Source → PostgreSQL**, paste
  the values above, **Test Connection → OK**.
- Or the optional web GUI: `docker compose --profile tools up -d pgadmin` →
  <http://localhost:5050> (`dev@eventmap.local` / `admin`), add a server with
  host `db`, port `5432`.
- Why 5433? A native PostgreSQL install (e.g. the Windows service
  `postgresql-x64-15`) usually owns 5432, and then `localhost:5432` may reach
  that server instead of the container — symptom: `password authentication
  failed for user "eventmap"` (SQL state 28P01) during Flyway. Keep Docker on
  5433, or stop that service if you do not need it.
- Something else on 5433? `$env:DB_PORT=5434; docker compose up -d db` and start
  the app with `DB_URL=jdbc:postgresql://localhost:5434/eventmap`.

Useful queries:

```sql
-- seeded cities and their centres
SELECT slug, name, ST_AsText(ST_Centroid(boundary::geometry)) FROM city ORDER BY name;

-- events plotted on the map
SELECT e.title, v.name, ST_Y(v.location::geometry) AS lat, ST_X(v.location::geometry) AS lon
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE e.status = 'PUBLISHED';

-- moderation queue
SELECT title, status, created_at FROM event WHERE status = 'PENDING';
```

### Schema and seed data

Flyway migrations in `src/main/resources/db/migration` are the only source of
truth. Hibernate runs with `ddl-auto: validate`, so a mismatch between an entity
and the schema fails the boot instead of silently altering the database.

| Migration              | Contents                                              |
| ---------------------- | ----------------------------------------------------- |
| `V1__init.sql`         | tables, enums-as-checks, geography columns, GiST indexes |
| `V2__seed_categories.sql` | 8 reference categories                             |
| `V3__seed_cities.sql`  | boundaries of the 7 launch cities                     |

City boundaries are real OpenStreetMap outlines, simplified to ~80 m. To
regenerate them (e.g. to add a city):

```powershell
.\tools\fetch-city-boundaries.ps1
```

It fetches Nominatim GeoJSON, simplifies it with PostGIS
`ST_SimplifyPreserveTopology(0.0008)` and rewrites `V3__seed_cities.sql`.
Because the file is already applied on existing databases, changing it in place
means either a new migration or `docker compose down -v` locally.

Demo data (`DevDataSeeder`, local profile only) adds 12 Kraków venues and ~20
events plus one `PENDING` submission, and never runs in production.

## API

Base path `/api/v1`. Errors are `application/problem+json` with a stable `code`
extension and, for validation errors, a `fieldErrors` array.

### Public

| Method | Path              | Notes                                                        |
| ------ | ----------------- | ------------------------------------------------------------ |
| GET    | `/map/events`     | `bbox=minLon,minLat,maxLon,maxLat` and `zoom` (0–19). Below `app.map.cluster-max-zoom` returns grid clusters, above it individual events. Capped at 500 rows (`truncated` flags the cap). |
| GET    | `/events`         | `city`, `category`, `from`, `to`, `limit` (1–50), `cursor`. One row per upcoming occurrence. |
| GET    | `/events/{id}`    | Published events only.                                       |
| GET    | `/categories`     | Reference data.                                              |
| GET    | `/cities`         | Slug + display name.                                         |

### Authenticated (`Authorization: Bearer <access token>`)

| Method | Path             | Notes                                      |
| ------ | ---------------- | ------------------------------------------ |
| POST   | `/auth/register` | Returns access + refresh tokens.           |
| POST   | `/auth/login`    |                                            |
| POST   | `/auth/refresh`  | `{"refreshToken": "..."}`.                 |
| POST   | `/events`        | Submits an event as `PENDING`.             |
| GET    | `/me/events`     | Your submissions and their status.         |

### Admin (`ROLE_ADMIN`)

| Method | Path                        | Notes                                |
| ------ | --------------------------- | ------------------------------------ |
| GET    | `/admin/events`             | `status`, `page`, `size` moderation queue |
| GET    | `/admin/events/{id}`        | Full detail including unpublished    |
| POST   | `/admin/events`             | Creates and publishes in one step    |
| POST   | `/admin/events/{id}/approve`| `204`                                 |
| POST   | `/admin/events/{id}/reject` | `{"reason": "..."}`                   |
| POST   | `/admin/media`              | multipart `file`: JPEG/PNG/WebP, 5 MB |

Example session:

```powershell
$login = curl.exe -s -X POST "http://localhost:8080/api/v1/auth/login" `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"admin@eventmap.local\",\"password\":\"admin12345\"}" | ConvertFrom-Json

curl.exe -s "http://localhost:8080/api/v1/admin/events?status=PENDING" `
  -H "Authorization: Bearer $($login.accessToken)"
```

Event lifecycle: `DRAFT → PENDING → PUBLISHED` or `REJECTED` (a rejected event
may be published after review). Only `PUBLISHED` events appear in public reads
and on the map. One cover image per event; the upload returns the URL to store
in `coverImageUrl`.

## Tests

```powershell
.\mvnw.cmd verify
```

`*IT` classes run under Failsafe against a real `postgis/postgis:17-3.5`
Testcontainers instance and talk to the application over real HTTP. Docker must
be running. The first run pulls the image and takes ~1 minute; later runs reuse
the container and finish in seconds. The suite covers the full moderation
lifecycle, JWT type separation, validation errors, map clustering vs. events,
and keyset pagination.

`.\mvnw.cmd test` runs only unit tests (currently none), `-DskipTests` builds
without them.

## Configuration

Base values live in `application.yaml`; `local`, `prod` and `test` override them.
`AppProperties` validates the whole contract at startup, so a missing or
malformed value fails the boot with a clear message.

| Property                            | Env var                       | prod |
| ----------------------------------- | ----------------------------- | ---- |
| `spring.datasource.url`             | `DB_URL`                      | required |
| `spring.datasource.username`        | `DB_USER`                     | default `eventmap` |
| `spring.datasource.password`        | `DB_PASSWORD`                 | required |
| `app.security.jwt-secret`           | `JWT_SECRET`                  | required, ≥ 32 chars |
| `app.admin.bootstrap-email`         | `ADMIN_EMAIL`                 | required |
| `app.admin.bootstrap-password`      | `ADMIN_PASSWORD`              | required |
| `app.media.storage-dir`             | `MEDIA_DIR`                   | `/app/data/media` |
| `app.map.max-results`               | –                             | 500 |
| `app.map.cluster-max-zoom`          | –                             | 12 |
| `app.auth.rate-limit-per-minute`    | `AUTH_RATE_LIMIT_PER_MINUTE`  | 20 |
| `app.web.allowed-origins`           | `APP_WEB_ALLOWED_ORIGINS`     | frontend origin |

Notes:

- The administrator is created on first boot from `ADMIN_EMAIL` / `ADMIN_PASSWORD`
  and is idempotent afterwards. Changing the env vars later does not change an
  existing account (there is no password-reset flow yet).
- The auth rate limiter is a fixed window in memory, per client IP, on
  `/api/v1/auth/**`. It is correct for this single-instance deployment; scaling
  horizontally requires moving it to Redis.
- `springdoc` is enabled by default and disabled by the `prod` profile.

## Deployment

Single host, Docker Compose, Caddy for TLS and static media.

1. Install Docker Engine + Compose plugin on the server.
2. Point a DNS record for your domain at the host; open ports 80 and 443.
3. Copy the repo (or just the image) to the server and create the env file:

   ```bash
   cp .env.example .env
   # set DOMAIN, ACME_EMAIL, DB_PASSWORD, JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD
   openssl rand -base64 48   # usable as JWT_SECRET
   chmod 600 .env
   ```

4. Build and start:

   ```bash
   docker compose -f compose.prod.yaml up -d --build
   docker compose -f compose.prod.yaml logs -f app
   ```

5. Verify: `https://$DOMAIN/actuator/health` returns `{"status":"UP"}`.

What the stack does:

- `db` — PostGIS, no published port, data in the `db-data` volume.
- `app` — the API image, `SPRING_PROFILES_ACTIVE=prod`, runs Flyway on boot.
  Not published to the host; only Caddy reaches it.
- `caddy` — terminates TLS (automatic Let's Encrypt), proxies everything to
  `app:8080`, and serves `/media/*` from the shared volume itself.
- Uploaded covers live on the `media-data` volume, shared read-only with Caddy.

Operational commands:

```bash
# logs
docker compose -f compose.prod.yaml logs -f app

# database backup (write it off the host afterwards)
docker compose -f compose.prod.yaml exec -T db \
  pg_dump -U eventmap -d eventmap -Fc > backup-$(date +%F).dump

# restore
docker compose -f compose.prod.yaml exec -T db \
  pg_restore -U eventmap -d eventmap --clean --if-exists < backup-2026-10-27.dump

# deploy a new version
docker compose -f compose.prod.yaml pull app && docker compose -f compose.prod.yaml up -d
```

Flyway applies pending migrations when the new container starts. Keep migrations
backwards compatible for one release (add columns as nullable, deploy, backfill)
so a rollback to the previous image still works.

## Project layout

```
src/main/java/pl/eventmap
├── auth/       users, JWT, security filter, bootstrap admin
├── category/   reference taxonomy
├── city/       city boundaries
├── venue/      physical locations (geography Point)
├── event/      event template + occurrences, submission, moderation
├── geo/        map viewport query, clustering, bbox parsing
├── media/      ImageStorage seam + local-disk implementation
├── seed/       local-only demo data
├── config/     typed properties, security, CORS, OpenAPI, clock
└── common/     problem+json, cursor codec, rate limiter
```

Packages are feature-first; inside a feature, `domain` (entities, repositories),
`app` (services, transactions) and `api` (controllers, DTOs) are separated. Writes
go through JPA/Hibernate with `hibernate-spatial`; the two hot read paths (the map
viewport and the event list) are hand-written SQL over `NamedParameterJdbcTemplate`,
which keeps the query shape equal to the API contract.

Design decisions and their trade-offs are recorded in
[`docs/decisions.md`](docs/decisions.md).

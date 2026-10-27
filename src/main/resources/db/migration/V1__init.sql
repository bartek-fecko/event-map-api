-- Baseline schema. PostGIS is a hard requirement: every location column is
-- geography (metres, WGS84), not geometry (degrees).
CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------
CREATE TABLE app_user
(
    id            uuid PRIMARY KEY,
    email         varchar(320) NOT NULL,
    password_hash varchar(100) NOT NULL,
    display_name  varchar(120) NOT NULL,
    role          varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_user_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX app_user_email_lower_uidx ON app_user (lower(email));

-- ---------------------------------------------------------------------------
-- Geography
-- ---------------------------------------------------------------------------
CREATE TABLE city
(
    id       uuid PRIMARY KEY,
    slug     varchar(80)  NOT NULL,
    name     varchar(120) NOT NULL,
    boundary geography(MultiPolygon, 4326) NOT NULL,
    CONSTRAINT city_slug_unique UNIQUE (slug)
);

CREATE INDEX city_boundary_gix ON city USING gist (boundary);

CREATE TABLE venue
(
    id         uuid PRIMARY KEY,
    name       varchar(200) NOT NULL,
    address    varchar(300) NOT NULL,
    city_id    uuid         NOT NULL REFERENCES city (id),
    location   geography(Point, 4326) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX venue_location_gix ON venue USING gist (location);
CREATE INDEX venue_city_idx ON venue (city_id);

-- ---------------------------------------------------------------------------
-- Taxonomy
-- ---------------------------------------------------------------------------
CREATE TABLE category
(
    id    uuid PRIMARY KEY,
    slug  varchar(60) NOT NULL,
    name  varchar(80) NOT NULL,
    color varchar(9)  NOT NULL,
    icon  varchar(60),
    CONSTRAINT category_slug_unique UNIQUE (slug)
);

-- ---------------------------------------------------------------------------
-- Events
-- ---------------------------------------------------------------------------
CREATE TABLE event
(
    id               uuid PRIMARY KEY,
    title            varchar(200) NOT NULL,
    description      text,
    status           varchar(20)  NOT NULL,
    organizer_id     uuid         NOT NULL REFERENCES app_user (id),
    venue_id         uuid         NOT NULL REFERENCES venue (id),
    cover_image_url  varchar(500),
    rejection_reason varchar(500),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT event_status_check
        CHECK (status IN ('DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX event_status_idx ON event (status);
CREATE INDEX event_venue_idx ON event (venue_id);
CREATE INDEX event_organizer_idx ON event (organizer_id);

CREATE TABLE event_category
(
    event_id    uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    category_id uuid NOT NULL REFERENCES category (id),
    PRIMARY KEY (event_id, category_id)
);

CREATE INDEX event_category_category_idx ON event_category (category_id);

-- An event is a template; every concrete date/time is an occurrence. A one-off
-- event simply has exactly one occurrence.
CREATE TABLE event_occurrence
(
    id         uuid PRIMARY KEY,
    event_id   uuid        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    starts_at  timestamptz NOT NULL,
    ends_at    timestamptz NOT NULL,
    status     varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    time_range tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at)) STORED,
    CONSTRAINT occurrence_time_check CHECK (ends_at > starts_at),
    CONSTRAINT occurrence_status_check CHECK (status IN ('SCHEDULED', 'CANCELLED'))
);

CREATE INDEX occurrence_event_idx ON event_occurrence (event_id);
CREATE INDEX occurrence_starts_idx ON event_occurrence (starts_at);
CREATE INDEX occurrence_range_gix ON event_occurrence USING gist (time_range);

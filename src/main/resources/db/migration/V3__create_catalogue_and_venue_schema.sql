-- 1. Cities
CREATE TABLE city (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    country_code VARCHAR(3) NOT NULL DEFAULT 'IND',
    time_zone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_city_name_state UNIQUE (name, state)
);

-- 2. Operator Organizations (Tenant Root e.g. PVR Inox, Cinepolis)
CREATE TABLE operator_organization (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_org_legal_name UNIQUE (legal_name)
);

-- 3. Cinemas (Physical Venues)
CREATE TABLE cinema (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES operator_organization(id) ON DELETE RESTRICT,
    city_id UUID NOT NULL REFERENCES city(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    time_zone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cinema_org_name UNIQUE (organization_id, name)
);

CREATE INDEX idx_cinema_city_id ON cinema(city_id);
CREATE INDEX idx_cinema_organization_id ON cinema(organization_id);

-- 4. Screens (Physical Auditoriums inside a Cinema)
CREATE TABLE screen (
    id UUID PRIMARY KEY,
    cinema_id UUID NOT NULL REFERENCES cinema(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_screen_cinema_name UNIQUE (cinema_id, name)
);

CREATE INDEX idx_screen_cinema_id ON screen(cinema_id);

-- 5. Seat Map Versions (Immutable Versioned Screen Layouts)
CREATE TABLE seat_map_version (
    id UUID PRIMARY KEY,
    screen_id UUID NOT NULL REFERENCES screen(id) ON DELETE RESTRICT,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_seats INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_screen_version UNIQUE (screen_id, version_number)
);

CREATE INDEX idx_seat_map_screen_id ON seat_map_version(screen_id);

-- 6. Seat Sections (e.g. RECLINER, PRIME, CLASSIC within a layout)
CREATE TABLE seat_section (
    id UUID PRIMARY KEY,
    seat_map_version_id UUID NOT NULL REFERENCES seat_map_version(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    category_code VARCHAR(20) NOT NULL,
    display_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_section_version_code UNIQUE (seat_map_version_id, category_code)
);

CREATE INDEX idx_seat_section_version_id ON seat_section(seat_map_version_id);

-- 7. Physical Seats (Individual Seats: Row A, Number 12)
CREATE TABLE physical_seat (
    id UUID PRIMARY KEY,
    seat_map_version_id UUID NOT NULL REFERENCES seat_map_version(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES seat_section(id) ON DELETE RESTRICT,
    row_label VARCHAR(10) NOT NULL,
    seat_number INT NOT NULL,
    seat_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_seat_version_row_number UNIQUE (seat_map_version_id, row_label, seat_number)
);

CREATE INDEX idx_physical_seat_version_id ON physical_seat(seat_map_version_id);
CREATE INDEX idx_physical_seat_section_id ON physical_seat(section_id);

-- 8. Movie Formats (Data-driven formats e.g. 2D, 3D, IMAX_2D, 4DX)
CREATE TABLE movie_format (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. Movies
CREATE TABLE movie (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    synopsis TEXT,
    runtime_minutes INT NOT NULL,
    certificate VARCHAR(10) NOT NULL DEFAULT 'UA',
    release_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RELEASED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_movie_release_date ON movie(release_date);
CREATE INDEX idx_movie_status ON movie(status);
CREATE INDEX idx_movie_title ON movie(title);

-- 10. Movie Supported Formats (Which movie supports which format)
CREATE TABLE movie_supported_format (
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    format_id UUID NOT NULL REFERENCES movie_format(id) ON DELETE RESTRICT,
    PRIMARY KEY (movie_id, format_id)
);

-- 11. Movie Languages (e.g. HINDI, ENGLISH, TAMIL, TELUGU)
CREATE TABLE movie_language (
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    language_code VARCHAR(20) NOT NULL,
    PRIMARY KEY (movie_id, language_code)
);

-- 12. Movie Genres (e.g. ACTION, SCI_FI, THRILLER)
CREATE TABLE movie_genre (
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    genre VARCHAR(50) NOT NULL,
    PRIMARY KEY (movie_id, genre)
);
-- Migration script for Integer-Based Coordinate System
-- This SQL corresponds to the changes in app/models.py

-- Create tracks table
CREATE TABLE tracks (
    id SERIAL PRIMARY KEY,
    user_uuid VARCHAR NOT NULL,
    device_id VARCHAR,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    start_lat_i INTEGER,
    start_lng_i INTEGER,
    end_lat_i INTEGER,
    end_lng_i INTEGER,
    duration_sec INTEGER DEFAULT 0,
    distance_m FLOAT DEFAULT 0.0,
    raw_point_count INTEGER DEFAULT 0,
    compressed_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (user_uuid) REFERENCES users(uuid)
);

CREATE INDEX ix_tracks_user_uuid ON tracks (user_uuid);
CREATE INDEX ix_tracks_id ON tracks (id);

-- Create track_points_raw table
CREATE TABLE track_points_raw (
    id SERIAL PRIMARY KEY,
    track_id INTEGER,
    seq INTEGER,
    time_offset INTEGER,
    lat_i INTEGER,
    lng_i INTEGER,
    speed_cms INTEGER,
    heading_deg INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (track_id) REFERENCES tracks(id)
);

CREATE INDEX ix_track_points_raw_track_id ON track_points_raw (track_id);
CREATE INDEX ix_track_points_raw_id ON track_points_raw (id);

-- Create track_points_compressed table
CREATE TABLE track_points_compressed (
    id SERIAL PRIMARY KEY,
    track_id INTEGER,
    seq INTEGER,
    time_offset INTEGER,
    lat_i INTEGER,
    lng_i INTEGER,
    is_corner BOOLEAN DEFAULT FALSE,
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (track_id) REFERENCES tracks(id)
);

CREATE INDEX ix_track_points_compressed_track_id ON track_points_compressed (track_id);
CREATE INDEX ix_track_points_compressed_id ON track_points_compressed (id);

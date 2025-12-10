from sqlalchemy import Column, String, Float, DateTime, ForeignKey, Integer, Boolean, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from .database import Base

class User(Base):
    __tablename__ = "users"

    uuid = Column(String, primary_key=True, index=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    created_lat = Column(Float)
    created_long = Column(Float)
    nickname = Column(String, unique=True, nullable=True)

    user_info = relationship("UserInfo", back_populates="user", uselist=False)
    usage_logs = relationship("UsageLog", back_populates="user")
    tracks = relationship("Track", back_populates="user")

class UserInfo(Base):
    __tablename__ = "user_info"

    id = Column(Integer, primary_key=True, index=True)
    user_uuid = Column(String, ForeignKey("users.uuid"), unique=True)
    
    # Encrypted Fields
    name = Column(String, nullable=True)
    password = Column(String, nullable=True) # Encrypted
    phone_number = Column(String, nullable=True)
    age = Column(String, nullable=True) # Stored as string for encryption
    address = Column(String, nullable=True)
    address_lat = Column(String, nullable=True) # Encrypted string
    address_long = Column(String, nullable=True) # Encrypted string
    work_address = Column(String, nullable=True)
    work_lat = Column(String, nullable=True) # Encrypted string
    work_long = Column(String, nullable=True) # Encrypted string

    user = relationship("User", back_populates="user_info")

class UsageLog(Base):
    __tablename__ = "usage_logs"

    id = Column(Integer, primary_key=True, index=True)
    user_uuid = Column(String, ForeignKey("users.uuid"))
    timestamp = Column(DateTime(timezone=True), server_default=func.now())
    latitude = Column(Float)
    longitude = Column(Float)

    user = relationship("User", back_populates="usage_logs")

# --- New Models for IntCoordinate System ---

class Track(Base):
    __tablename__ = "tracks"

    id = Column(Integer, primary_key=True, index=True)
    user_uuid = Column(String, ForeignKey("users.uuid"), index=True)
    device_id = Column(String, nullable=True) # To link to mobile device info if needed
    
    started_at = Column(DateTime(timezone=True))
    ended_at = Column(DateTime(timezone=True))
    
    start_lat_i = Column(Integer)
    start_lng_i = Column(Integer)
    end_lat_i = Column(Integer)
    end_lng_i = Column(Integer)
    
    duration_sec = Column(Integer, default=0)
    distance_m = Column(Float, default=0.0)
    
    raw_point_count = Column(Integer, default=0)
    compressed_count = Column(Integer, default=0)
    
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    
    user = relationship("User", back_populates="tracks")
    raw_points = relationship("TrackPointRaw", back_populates="track", cascade="all, delete-orphan")
    compressed_points = relationship("TrackPointCompressed", back_populates="track", cascade="all, delete-orphan")

class TrackPointRaw(Base):
    __tablename__ = "track_points_raw"

    id = Column(Integer, primary_key=True, index=True)
    track_id = Column(Integer, ForeignKey("tracks.id"), index=True)
    seq = Column(Integer) # Sequence number 0, 1, 2...
    
    time_offset = Column(Integer) # Seconds from track start
    lat_i = Column(Integer)
    lng_i = Column(Integer)
    
    speed_cms = Column(Integer, nullable=True) # Speed in cm/s
    heading_deg = Column(Integer, nullable=True) # 0-360
    
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    
    track = relationship("Track", back_populates="raw_points")

class TrackPointCompressed(Base):
    __tablename__ = "track_points_compressed"

    id = Column(Integer, primary_key=True, index=True)
    track_id = Column(Integer, ForeignKey("tracks.id"), index=True)
    seq = Column(Integer)
    
    time_offset = Column(Integer)
    lat_i = Column(Integer)
    lng_i = Column(Integer)
    
    is_corner = Column(Boolean, default=False)
    note = Column(Text, nullable=True)
    
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    
    track = relationship("Track", back_populates="compressed_points")

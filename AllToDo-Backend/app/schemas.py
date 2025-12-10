from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

# User Schemas
class UserBase(BaseModel):
    uuid: str # Changed from String to str

class UserCreate(BaseModel):
    uuid: str
    latitude: float
    longitude: float
    nickname: Optional[str] = None

class UserResponse(BaseModel):
    uuid: str
    created_at: datetime
    message: str

class RecoverRequest(BaseModel):
    nickname: str
    password: str

# Usage Log Schemas
class LogCreate(BaseModel):
    user_uuid: str
    latitude: float
    longitude: float

class LogResponse(BaseModel):
    status: str

# User Info Schemas (Input is plain text, output is plain text - encryption happens internally)
class UserInfoUpdate(BaseModel):
    user_uuid: str
    name: Optional[str] = None
    password: Optional[str] = None
    phone_number: Optional[str] = None
    age: Optional[int] = None
    address: Optional[str] = None
    address_lat: Optional[float] = None
    address_long: Optional[float] = None
    work_address: Optional[str] = None
    work_lat: Optional[float] = None
    work_long: Optional[float] = None

class UserInfoResponse(BaseModel):
    user_uuid: str
    name: Optional[str] = None
    phone_number: Optional[str] = None
    age: Optional[str] = None # Age is stored as string
    address: Optional[str] = None
    address_lat: Optional[str] = None
    address_long: Optional[str] = None
    work_address: Optional[str] = None
    work_lat: Optional[str] = None
    work_long: Optional[str] = None
    nickname: Optional[str] = None

class UserUpdateResponse(BaseModel):
    status: str

# --- New Schemas for IntCoordinate System ---

class TrackPointBase(BaseModel):
    seq: int
    time_offset: int
    lat_i: int
    lng_i: int

class TrackPointRawCreate(TrackPointBase):
    speed_cms: Optional[int] = None
    heading_deg: Optional[int] = None

class TrackPointCompressedCreate(TrackPointBase):
    is_corner: bool = False
    note: Optional[str] = None

class TrackCreate(BaseModel):
    user_uuid: str
    device_id: Optional[str] = None
    started_at: datetime
    ended_at: datetime
    start_lat_i: int
    start_lng_i: int
    end_lat_i: int
    end_lng_i: int
    duration_sec: int
    distance_m: float
    raw_point_count: int
    compressed_count: int
    
    raw_points: List[TrackPointRawCreate] = []
    compressed_points: List[TrackPointCompressedCreate] = []

class TrackResponse(BaseModel):
    id: int
    user_uuid: str
    started_at: datetime
    distance_m: float
    compressed_count: int
    
    class Config:
        from_attributes = True

class TrackDetailResponse(TrackResponse):
    compressed_points: List[TrackPointCompressedCreate]

# Remote Log
class RemoteLogCreate(BaseModel):
    level: str
    message: str
    device: str
    timestamp: float

class RemoteLogResponse(BaseModel):
    status: str


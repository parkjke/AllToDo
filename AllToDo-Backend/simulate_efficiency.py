
import sys
import os
import math
import random

# Add the current directory to sys.path
sys.path.append(os.getcwd())

from app.coords import IntCoordinate, TrajectoryCompressor

def generate_synthetic_track(duration_sec=3600, speed_mps=1.4):
    """
    Generates a synthetic GPS track:
    - Walking speed ~1.4 m/s (5 km/h)
    - 1 point per second
    - Includes straight lines and some turns to simulate real usage
    """
    points = []
    
    # Start at Seoul City Hall
    lat = 37.5665
    lng = 126.9780
    
    current_lat = lat
    current_lng = lng
    
    # Random walk simulation
    # 0 deg = North
    heading = 0 
    
    for t in range(duration_sec):
        # Every 10 seconds, adds slight noise, every 60 seconds maybe a turn
        
        # Add noise (GPS error ~ 1-2m simulated as slight jitter)
        # 1 deg lat = 111,000m. 1m = 1/111000 = 0.000009
        noise_lat = random.uniform(-0.000005, 0.000005)
        noise_lng = random.uniform(-0.000005, 0.000005)
        
        # Turn logic
        if t % 300 == 0: # Turn every 5 mins
            heading += random.choice([90, -90, 45, -45])
        
        # Move
        dist_deg = (speed_mps / 111000.0)
        d_lat = math.cos(math.radians(heading)) * dist_deg
        d_lng = math.sin(math.radians(heading)) * dist_deg
        
        current_lat += d_lat + noise_lat
        current_lng += d_lng + noise_lng
        
        points.append((current_lat, current_lng))
        
    return points

def run_simulation():
    print("=== Integer-Based Coordinate System Efficiency Simulation ===\n")
    
    # 1. Generate Data (1 Hour Walk)
    duration = 3600 # 1 hour
    points_raw_double = generate_synthetic_track(duration)
    print(f"[1] Data Generation")
    print(f"    - Type: 1 Hour Walk (GPS recorded every 1 sec)")
    print(f"    - Total Raw Points: {len(points_raw_double):,}")
    
    # 2. Storage Efficiency (Double vs Int)
    # Double = 8 bytes, Int = 4 bytes (in Postgres Integer)
    # Coordinates pair: Double(16 bytes) vs Int(8 bytes)
    raw_size_bytes = len(points_raw_double) * 16
    
    # Convert to IntCoordinate
    points_int = [IntCoordinate.from_double(lat, lng) for lat, lng in points_raw_double]
    int_size_bytes = len(points_int) * 8
    
    print(f"\n[2] Data Type Efficiency (Before Compression)")
    print(f"    - Double Precision (Legacy): {raw_size_bytes:,} bytes")
    print(f"    - Integer (New): {int_size_bytes:,} bytes")
    print(f"    - Reduction: 50% just by changing types")
    
    # 3. Compression Efficiency (Trajectory Compressor)
    # Apply Online Compression (min_dist=3m, angle=10deg)
    compressed_points = TrajectoryCompressor.online_compress(points_int, min_dist_m=3.0, angle_thresh_deg=10.0)
    
    compressed_count = len(compressed_points)
    compressed_size_bytes = compressed_count * 8 # Storing Ints
    
    print(f"\n[3] Compression Algorithm Efficiency")
    print(f"    - Algorithm: Distance(3m) + Angle(10°) Filter")
    print(f"    - Compressed Points: {compressed_count:,}")
    print(f"    - Compression Ratio: {((len(points_raw_double) - compressed_count) / len(points_raw_double)) * 100:.1f}% reduced")
    
    # 4. Total Storage Savings
    total_savings_pct = ((raw_size_bytes - compressed_size_bytes) / raw_size_bytes) * 100
    
    print(f"\n[4] Total Database Impact")
    print(f"    - Raw Storage (Legacy): {raw_size_bytes:,} bytes per hour/user")
    print(f"    - Optimized Storage: {compressed_size_bytes:,} bytes per hour/user")
    print(f"    - TOTAL SAVINGS: {total_savings_pct:.1f}%")
    
    # 5. Database Scale Simulation
    # Assume 10,000 users tracking 1 hour per day for 1 year
    total_raw_gb = (raw_size_bytes * 10000 * 365) / (1024**3)
    total_opt_gb = (compressed_size_bytes * 10000 * 365) / (1024**3)
    
    print(f"\n[5] Scale Simulation (10k users, 1 hr/day, 1 year)")
    print(f"    - Legacy DB Size: ~{total_raw_gb:.2f} GB")
    print(f"    - Optimized DB Size: ~{total_opt_gb:.2f} GB")
    print(f"    - Conclusion: Saves ~{total_raw_gb - total_opt_gb:.2f} GB of storage")

if __name__ == "__main__":
    run_simulation()

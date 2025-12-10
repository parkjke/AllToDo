
import sys
import os

# Add the current directory to sys.path so we can import app modules
sys.path.append(os.getcwd())

from app.coords import IntCoordinate, TrajectoryCompressor, GridCluster

def test_conversion():
    lat = 37.566512
    lng = 126.978123
    
    coord = IntCoordinate.from_double(lat, lng)
    print(f"Original: {lat}, {lng}")
    print(f"Integer:  {coord.lat}, {coord.lng}")
    
    lat_back, lng_back = coord.to_double()
    print(f"Restored: {lat_back}, {lng_back}")
    
    assert coord.lat == 3756651
    assert coord.lng == 12697812
    assert abs(lat - lat_back) < 0.00001
    assert abs(lng - lng_back) < 0.00001
    print("Conversion Test Passed")

def test_distance():
    p1 = IntCoordinate.from_double(37.5, 127.0)
    p2 = IntCoordinate.from_double(37.5001, 127.0) # approx 11m north
    
    dist = p1.distance_to(p2)
    print(f"Distance: {dist} meters")
    assert 10 < dist < 12 
    print("Distance Test Passed")

def test_compression():
    # p1 -> p2 (very close, keep same direction) -> p3 (far)
    # This shouldn't compress much with short distance unless we tweak params.
    
    points = [
        IntCoordinate.from_double(37.00000, 127.00000), # Start
        IntCoordinate.from_double(37.00001, 127.00001), # very close, ~1.5m
        IntCoordinate.from_double(37.00002, 127.00002), # very close, ~1.5m
        IntCoordinate.from_double(37.01000, 127.01000), # far, straight line
    ]
    
    # Min dist 3m. p2 is ~1.5m from p1 -> should be skipped?
    # Logic: 
    # Last=p0. Current=p1. Dist(p0,p1) ~1.5m < 3m. Skip p1?
    # Logic in code: if dist < min_dist -> skip angle check, loop continues.
    # So p1 is ignored.
    # Next: Current=p2. Last=p0. Dist(p0,p2) ~3.0m >= 3m. Check angle.
    # Bearing p0->p1 (skipped) vs p2 is ... wait.
    # My online compressor compares last_kept -> current.
    
    compressed = TrajectoryCompressor.online_compress(points, min_dist_m=2.0)
    print(f"Points: {len(points)} -> Compressed: {len(compressed)}")
    
    # Expect: p0 kept.
    # i=1 (p1): dist(p0, p1) ~ 1.5m < 2.0 -> skip.
    # i=2 (p2): dist(p0, p2) ~ 3.0m > 2.0 -> check angle.
    # bearing(p0, p2) vs bearing(p2, p3). 
    # They are all on the line y=x. Angle diff ~ 0.
    # Angle thresh 10 deg. 0 < 10 -> skip p2? 
    # Wait, if angle is small, we DON'T keep it (it's a straight line).
    # So p2 is skipped?
    # End of loop. Add last point p3.
    # Result: [p0, p3].
    
    for p in compressed:
        print(f"  {p.lat}, {p.lng}")
        
    assert len(compressed) == 2
    print("Compression Test Passed")

def test_clustering():
    points = [
        IntCoordinate(10000, 10000),
        IntCoordinate(10005, 10005), # Very close
        IntCoordinate(50000, 50000), # Far away
    ]
    
    # Zoom level 15.
    # Cell size logic: base=50 * 2^(15-15) = 50.
    # p1: 10000//50 = 200, 200
    # p2: 10005//50 = 200, 200 (Same cell)
    # p3: 50000//50 = 1000, 1000 (Diff cell)
    
    clusters = GridCluster.cluster(points, zoom_level=15)
    print(f"Clusters: {len(clusters)}")
    for k, v in clusters.items():
        print(f"  Key {k}: {len(v)} points")
        
    assert len(clusters) == 2
    assert len(clusters[(200, 200)]) == 2
    print("Clustering Test Passed")

if __name__ == "__main__":
    test_conversion()
    test_distance()
    test_compression()
    test_clustering()

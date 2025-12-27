import re
import os

def fix_file(path):
    with open(path, 'r') as f:
        content = f.read()
    
    # Pattern 1: case .userLocation(let coord), .serverMessage: marker.position = ...
    # Specific to Google/Naver single line or combined
    content = re.sub(
        r'case .userLocation\(let coord\), .serverMessage:\s+(marker\.position = [^\n]+)',
        r'case .userLocation(let coord):\n                    \1\n                case .serverMessage:\n                    break',
        content
    )

    # Pattern 2: Multi-line assignment
    # case .userLocation(let coord), .serverMessage:
    #     itemLat = coord.latitude; itemLon = coord.longitude
    content = re.sub(
        r'case .userLocation\(let coord\), .serverMessage:\s+(itemLat = coord\.latitude; itemLon = coord\.longitude)',
        r'case .userLocation(let coord):\n                    \1\n                case .serverMessage:\n                    break',
        content
    )

    # Pattern 3: marker.position = coord
    content = re.sub(
        r'case .userLocation\(let coord\), .serverMessage: marker\.position = coord',
        r'case .userLocation(let coord): marker.position = coord\n                      case .serverMessage: break',
        content
    )

    # Pattern 4: any remaining case .userLocation(let coord), .serverMessage:
    # Just split them and break for serverMessage
    content = re.sub(
        r'case .userLocation\(let coord\), .serverMessage:',
        r'case .userLocation(let coord):\n                    // Binding fixed\n                case .serverMessage:',
        content
    )
    
    # Cleanup duplicate .serverMessage cases that might arise
    content = re.sub(
        r'case .serverMessage:\s+break\s+case (?:.serverMessage:|.serverMessage:\s+break)',
        r'case .serverMessage:\n                    break',
        content
    )

    with open(path, 'w') as f:
        f.write(content)

files = [
    "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift",
    "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/NaverMapView.swift"
]

for f in files:
    if os.path.exists(f):
        fix_file(f)
        print(f"Processed {f}")


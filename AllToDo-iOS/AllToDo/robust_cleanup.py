import re

def fix(path):
    with open(path, 'r') as f:
        c = f.read()
    
    # 1. break break -> break
    c = re.sub(r'break\s+break', 'break', c)
    
    # 2. Fix the switch block with variable bindings
    # Look for: case .userLocation(let coord): <whitespace> marker.position = coord <whitespace> case .serverMessage: <whitespace> break
    p = r'case \.userLocation\(let coord\):\s*marker\.position = coord\s*case \.serverMessage:\s*break'
    r = 'case .userLocation(let coord):\n                          marker.position = coord\n                      case .serverMessage:\n                          break'
    c = re.sub(p, r, c)

    with open(path, 'w') as f:
        f.write(c)

fix("/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift")

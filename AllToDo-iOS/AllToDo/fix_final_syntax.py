import os

def fix_google(path):
    with open(path, 'r') as f:
        c = f.read()
    
    # Fix double break
    c = c.replace('break\n                    break', 'break')
    c = c.replace('break break', 'break')
    
    # Fix messy single logic block around line 593
    p_messy = \"\"\"                      case .userLocation(let coord):
                     marker.position = coord
                 case .serverMessage:
                     break\"\"\"
    r_proper = \"\"\"                      case .userLocation(let coord):
                          marker.position = coord
                      case .serverMessage:
                          break\"\"\"
    # Let's use a simpler match
    c = c.replace('case .userLocation(let coord):\n                     marker.position = coord\n                 case .serverMessage:\n                     break', r_proper)

    with open(path, 'w') as f:
        f.write(c)

def fix_naver(path):
    with open(path, 'r') as f:
        c = f.read()

    # Fix indentation in renderRawItems if needed (already mostly okay)
    # The multi_replace already fixed one Naver block.
    
    with open(path, 'w') as f:
        f.write(c)

fix_google("/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift")

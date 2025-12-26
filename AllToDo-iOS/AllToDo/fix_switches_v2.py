import os

def fix_switches(filename):
    if not os.path.exists(filename):
        print(f"File {filename} not found")
        return
    
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # Pattern 1: case .userLocation(let coord), .serverMessage: marker.position = NMGLatLng(lat: coord.latitude, lng: coord.longitude)
        if 'case .userLocation(let coord), .serverMessage: marker.position =' in line:
            indent = line[:line.find('case')]
            # Extract part after colon
            after_colon = line[line.find(':')+1:].strip()
            new_lines.append(f"{indent}case .userLocation(let coord): {after_colon}\n")
            new_lines.append(f"{indent}case .serverMessage: break\n")
            i += 1
            continue

        # Pattern 2: case .userLocation(let coord), .serverMessage: marker.position = coord
        if 'case .userLocation(let coord), .serverMessage: marker.position = coord' in line:
            indent = line[:line.find('case')]
            new_lines.append(f"{indent}case .userLocation(let coord): marker.position = coord\n")
            new_lines.append(f"{indent}case .serverMessage: break\n")
            i += 1
            continue

        # Pattern 3: case .userLocation(let coord), .serverMessage:
        #          marker.position = coord
        # (Combined case spanning two lines)
        if 'case .userLocation(let coord), .serverMessage:' in line:
            indent = line[:line.find('case')]
            # Check next line
            if i + 1 < len(lines):
                next_line = lines[i+1]
                if 'marker.position = coord' in next_line:
                    new_lines.append(f"{indent}case .userLocation(let coord):\n")
                    new_lines.append(next_line)
                    new_lines.append(f"{indent}case .serverMessage: break\n")
                    i += 2
                    # Potential duplicate next?
                    if i < len(lines) and 'case .serverMessage:' in lines[i]:
                        i += 1
                        if i < len(lines) and 'break' in lines[i]: i += 1
                    continue
                
                if 'itemLat = coord.latitude;' in next_line:
                    new_lines.append(f"{indent}case .userLocation(let coord):\n")
                    new_lines.append(next_line)
                    new_lines.append(f"{indent}case .serverMessage: break\n")
                    i += 2
                    if i < len(lines) and 'case .serverMessage:' in lines[i]:
                        i += 1
                        if i < len(lines) and 'break' in lines[i]: i += 1
                    continue

        new_lines.append(line)
        i += 1
    
    with open(filename, 'w') as f:
        f.writelines(new_lines)

fix_switches("/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift")
fix_switches("/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/NaverMapView.swift")

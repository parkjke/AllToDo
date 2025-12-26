def cleanup(path):
    with open(path, 'r') as f:
        lines = f.readlines()
    
    new_lines = []
    for i in range(len(lines)):
        line = lines[i]
        # Fix double break
        line = line.replace('break break', 'break')
        
        # Indentation fix for GoogleMapView messy block
        if 'marker.position = coord' in line and i > 0 and 'case .userLocation(let coord):' in lines[i-1]:
            line = '                          marker.position = coord\n'
        if 'case .serverMessage:' in line and i > 0 and 'marker.position = coord' in lines[i-1]:
            line = '                      case .serverMessage:\n'
        if 'break' in line and i > 0 and 'case .serverMessage:' in lines[i-1] and 'GoogleMapView' in path:
            line = '                          break\n'

        new_lines.append(line)
        
    with open(path, 'w') as f:
        f.writelines(new_lines)

cleanup("/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift")
cleanup("/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/NaverMapView.swift")

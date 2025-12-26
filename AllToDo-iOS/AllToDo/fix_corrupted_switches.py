import re
import os

def fix_switches(content):
    # 1. Fix combined case .userLocation(let coord), .serverMessage: with latSum/lonSum
    p1 = r'case .userLocation\(let coord\), .serverMessage:\s+latSum \+= coord.latitude\s+lonSum \+= coord.longitude\s+count \+= 1'
    r1 = 'case .userLocation(let coord):\n                        latSum += coord.latitude\n                        lonSum += coord.longitude\n                        count += 1\n                    case .serverMessage:\n                        break'
    content = re.sub(p1, r1, content)

    # 2. Fix combined case .userLocation(let coord), .serverMessage: with allItemsToProcess / rawPoints
    p2 = r'case .userLocation\(let coord\), .serverMessage:\s+allItemsToProcess.append\(item\)\s+rawPoints.append\(Int32\(coord.latitude \* 100_000\)\)\s+rawPoints.append\(Int32\(coord.longitude \* 100_000\)\)'
    r2 = 'case .userLocation(let coord):\n                    allItemsToProcess.append(item)\n                    rawPoints.append(Int32(coord.latitude * 100_000))\n                    rawPoints.append(Int32(coord.longitude * 100_000))\n                case .serverMessage:\n                    break'
    content = re.sub(p2, r2, content)

    # 3. Fix combined case .userLocation(let coord), .serverMessage: with bounds
    p3 = r'case .userLocation\(let coord\), .serverMessage:\s+bounds = bounds.includingCoordinate\(coord\)'
    r3 = 'case .userLocation(let coord):\n                      bounds = bounds.includingCoordinate(coord)\n                  case .serverMessage:\n                      break'
    content = re.sub(p3, r3, content)
    
    # 4. Remove duplicate .serverMessage cases created by redundant sed/manual edits
    # This matches block like:
    # case .serverMessage:
    #    break
    # case .serverMessage:
    #    break
    # We want to keep only one.
    
    # Fix the messy centroid block specifically if still present
    p_messy_centroid = r'case .serverMessage:\s+break // Or handle as needed\s+case .userLocation\(let coord\):\s+latSum \+= coord.latitude\s+lonSum \+= coord.longitude\s+count \+= 1\s+case (?:.serverMessage: break|.serverMessage:\s+break)'
    r_messy_centroid = 'case .userLocation(let coord):\n                        latSum += coord.latitude\n                        lonSum += coord.longitude\n                        count += 1\n                    case .serverMessage:\n                        break'
    content = re.sub(p_messy_centroid, r_messy_centroid, content)

    return content

files = [
    "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift",
    "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/NaverMapView.swift"
]

for fp in files:
    if os.path.exists(fp):
        with open(fp, 'r') as f:
            c = f.read()
        new_c = fix_switches(c)
        if new_c != c:
            with open(fp, 'w') as f:
                f.write(new_c)
            print(f"Fixed {fp}")
        else:
            print(f"No changes for {fp}")


import re

def fix_google(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    # 1. Fix Centroid Fallback (Line 58)
    # 2. Fix renderRawItems (Line 162, 175)
    # 3. Fix zoomToFit (Line 220)
    # 4. Fix refreshWasmClusters (Line 417, 469)
    # 5. Fix renderWasmResults buckets (Line 542)
    # 6. Fix icon selection (Line 607+)
    # 7. Fix performLaunchAnimation (Line 646+)
    
    # Let's do selective replacements for the ones that usually fail
    
    # performLaunchAnimation
    p_launch = r'(case .userLocation\(let coord\):\s+bounds = bounds.includingCoordinate\(coord\))(\s+})(\s+})(\s+\/\/ Apply Fit)'
    content = re.sub(p_launch, r'\1\n                  case .serverMessage:\n                      break\2\3\4', content)
    
    with open(file_path, 'w') as f:
        f.write(content)

def fix_naver(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Centroid Fallback
    p_centroid = r'for item in todoItems \{.*?for log in userLogs \{.*?\}'
    new_centroid = """for item in parent.allItems {
                    switch item {
                    case .todo(let t):
                        latSum += t.latitude
                        lonSum += t.longitude
                        count += 1
                    case .history(let log):
                        latSum += log.latitude
                        lonSum += log.longitude
                        count += 1
                    case .userLocation(let coord):
                        latSum += coord.latitude
                        lonSum += coord.longitude
                        count += 1
                    case .serverMessage:
                        break
                    }
                }"""
    content = re.sub(p_centroid, new_centroid, content, flags=re.DOTALL)
    
    with open(file_path, 'w') as f:
        f.write(content)

# We can also just use simple string replacements if the blocks are unique enough
def replace_blocks():
    # Naver Centennial Fallback
    naver_path = "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/NaverMapView.swift"
    with open(naver_path, 'r') as f:
        c = f.read()
    
    legacy_nv = \"\"\"                for item in todoItems {
                    if let loc = item.location {
                        latSum += loc.latitude
                        lonSum += loc.longitude
                        count += 1
                    }
                }
                for log in userLogs {
                    latSum += log.latitude
                    lonSum += log.longitude
                    count += 1
                }\"\"\"
    unified_nv = \"\"\"                for item in parent.allItems {
                    switch item {
                    case .todo(let t):
                        latSum += t.latitude
                        lonSum += t.longitude
                        count += 1
                    case .history(let log):
                        latSum += log.latitude
                        lonSum += log.longitude
                        count += 1
                    case .userLocation(let coord):
                        latSum += coord.latitude
                        lonSum += coord.longitude
                        count += 1
                    case .serverMessage:
                        break
                    }
                }\"\"\"
    if legacy_nv in c:
        c = c.replace(legacy_nv, unified_nv)
        with open(naver_path, 'w') as f:
            f.write(c)

replace_blocks()

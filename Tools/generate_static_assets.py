import os
import re
import json
import math

# Paths
BASE_DIR = "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Assets.xcassets"
SHIELD_DIR = os.path.join(BASE_DIR, "Components/Shields")
MARK_DIR = os.path.join(BASE_DIR, "Components/Marks")
OUTPUT_DIR = os.path.join(BASE_DIR, "Pins")

# Configuration
SHIELD_LOGICAL_WIDTH = 100.0
SHIELD_LOGICAL_HEIGHT = 125.0
MARK_TARGET_HEIGHT = 65.0 # 52% of 125
MARK_TOP_Y = 17.5         # Center Y (50) - Half Height (32.5) = 17.5

MARKS = [
    "00", "01", "02",
    "10", "11", "12", "13", "14",
    "20", "21", "22", "23", "24"
]

def get_shield_name(mark_id):
    if mark_id.startswith("0"): return "pin_shield_0X"
    if mark_id.startswith("1"): return "pin_shield_1X"
    if mark_id.startswith("2"): return "pin_shield_2X"
    return "pin_shield_1X"

def extract_viewbox_and_content(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Extract viewBox
    vb_match = re.search(r'viewBox="([^"]+)"', content)
    viewbox = [0, 0, 100, 100] # Default
    if vb_match:
        parts = vb_match.group(1).split()
        if len(parts) == 4:
            viewbox = [float(p) for p in parts]
    
    # Extract width/height if available, fallback to viewBox
    w_match = re.search(r'width="([^"]+)"', content)
    h_match = re.search(r'height="([^"]+)"', content)
    
    width = float(w_match.group(1).replace("px","")) if w_match else viewbox[2]
    height = float(h_match.group(1).replace("px","")) if h_match else viewbox[3]
    
    # Extract Content (everything inside <svg>...</svg>)
    # Regex to grab content between signature
    # Simple strategy: find first > after <svg and last </svg>
    start_tag_end = content.find(">")
    end_tag_start = content.rfind("</svg>")
    
    if start_tag_end != -1 and end_tag_start != -1:
        inner_content = content[start_tag_end+1 : end_tag_start]
    else:
        inner_content = ""
        
    return {
        "width": width, 
        "height": height, 
        "viewBox": viewbox, 
        "content": inner_content
    }

def create_imageset(name):
    path = os.path.join(OUTPUT_DIR, f"{name}.imageset")
    if not os.path.exists(path):
        os.makedirs(path)
    
    contents = {
        "images": [
            {
                "filename": f"{name}.svg",
                "idiom": "universal",
                "scale": "1x" # SVG is vector, scale 1x usually implies "use this for all" with "preserve vector"
            }
        ],
        "info": {
            "author": "xcode",
            "version": 1
        },
        "properties": {
            "preserves-vector-representation": True 
        }
    }
    
    with open(os.path.join(path, "Contents.json"), 'w') as f:
        json.dump(contents, f, indent=4)
        
    return path

def main():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)
        
    for mark_id in MARKS:
        shield_name = get_shield_name(mark_id)
        mark_name = f"pin_mark_{mark_id}"
        output_name = f"map_pin_{mark_id}"
        
        print(f"Generating {output_name} ({shield_name} + {mark_name})...")
        
        # Paths
        shield_path = os.path.join(SHIELD_DIR, f"{shield_name}.imageset", f"{shield_name}.svg")
        mark_path = os.path.join(MARK_DIR, f"{mark_name}.imageset", f"{mark_name}.svg")
        
        # Read
        shield_data = extract_viewbox_and_content(shield_path)
        mark_data = extract_viewbox_and_content(mark_path)
        
        # Calculate Transform
        # Shield is Ref (100x125)
        scale = MARK_TARGET_HEIGHT / mark_data['height']
        scaled_width = mark_data['width'] * scale
        
        pos_x = (SHIELD_LOGICAL_WIDTH - scaled_width) / 2
        pos_y = MARK_TOP_Y
        
        # SVG Composition
        svg_out = f'''<svg width="200" height="250" viewBox="0 0 100 125" xmlns="http://www.w3.org/2000/svg">
    <g id="shield">
        {shield_data['content']}
    </g>
    <g id="mark" transform="translate({pos_x:.2f}, {pos_y:.2f}) scale({scale:.4f})">
        {mark_data['content']}
    </g>
</svg>'''
        
        # Write Imageset
        imageset_path = create_imageset(output_name)
        
        # Generate PNG using rsvg-convert
        # Standard size is 40x50pt. @3x is 120x150px.
        # SVG logic size is 100x125.
        # So we want to scale logic units to pixels.
        # 100 logic -> 120 px => scale factor 1.2
        # rsvg-convert default is 90dpi?
        # Better: specify width/height directly.
        
        png_filename = f"{output_name}.png"
        png_path = os.path.join(imageset_path, png_filename)
        
        # Write temp SVG for conversion (safer than pipe sometimes)
        temp_svg = os.path.join(imageset_path, "temp.svg")
        with open(temp_svg, 'w') as f:
            f.write(svg_out)
            
        # Run rsvg-convert
        # Target @3x size: 120x150 pixels (40pt x 3)
        # Note: logic size is 100x125.
        # command: rsvg-convert -w 120 -h 150 -o output.png input.svg
        cmd = f"/opt/homebrew/bin/rsvg-convert -w 120 -h 150 -o {png_path} {temp_svg}"
        os.system(cmd)
        
        # Clean up temp
        os.remove(temp_svg)
        
        # Update Contents.json to point to PNG
        with open(os.path.join(imageset_path, "Contents.json"), 'r') as f:
            contents = json.load(f)
            
        contents["images"][0]["filename"] = png_filename
        contents["images"][0]["scale"] = "3x" # We generated @3x
        # Add placeholders for 1x, 2x if needed, but 3x usually scales down fine or Xcode handles universal
        # Actually for bitmaps, explicit scales are better. But let's start with 3x Universal.
        
        with open(os.path.join(imageset_path, "Contents.json"), 'w') as f:
            json.dump(contents, f, indent=4)
            
    print("✅ All static assets (PNG Bitmaps) generated in Assets.xcassets/Pins/")

if __name__ == "__main__":
    main()

import os
import json

# Paths
PROJECT_ROOT = "/Volumes/Work/AllToDo"
MERGE_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/merge")
BASE_DIR = "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Assets.xcassets"
OUTPUT_DIR = os.path.join(BASE_DIR, "Pins")

# List of marks to process
MARKS = [
    "00", "01", "02",
    "10", "11", "12", "13", "14",
    "20", "21", "22", "23", "24"
]

def create_imageset(name):
    path = os.path.join(OUTPUT_DIR, f"{name}.imageset")
    if not os.path.exists(path):
        os.makedirs(path)
    
    contents = {
        "images": [
            {
                "filename": f"{name}.png",
                "idiom": "universal",
                "scale": "3x"
            }
        ],
        "info": {
            "author": "xcode",
            "version": 1
        },
        "properties": {
             # generated from bitmap, so maybe false or omit, but staying consistent
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
        merge_filename = f"pin_merge_{mark_id}.svg"
        merge_path = os.path.join(MERGE_DIR, merge_filename)
        output_name = f"map_pin_{mark_id}"
        
        if not os.path.exists(merge_path):
            print(f"⚠️ Warning: Merged source {merge_filename} not found at {merge_path}")
            continue
            
        print(f"Generating {output_name} from {merge_filename}...")
        
        # Create .imageset directory
        imageset_path = create_imageset(output_name)
        png_path = os.path.join(imageset_path, f"{output_name}.png")
        
        # Run rsvg-convert using the MERGED SVG as source
        # Target @3x size: 120x150 pixels (40pt x 3)
        # command: rsvg-convert -w 120 -h 150 -o output.png input.svg
        cmd = f"/opt/homebrew/bin/rsvg-convert -w 120 -h 150 -o {png_path} {merge_path}"
        ret = os.system(cmd)
        
        if ret != 0:
            print(f"❌ Error generating PNG for {output_name}")
        
    print("✅ All static assets (PNG Bitmaps) updated from Merged SVGs.")

if __name__ == "__main__":
    main()

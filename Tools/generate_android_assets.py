import os
import shutil

# Paths
PROJECT_ROOT = "/Volumes/Work/AllToDo"
MERGE_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/merge")
ANDROID_RES_DIR = os.path.join(PROJECT_ROOT, "AllToDo-Android/app/src/main/res")
TARGET_DIR = os.path.join(ANDROID_RES_DIR, "drawable-xxxhdpi")

# List of marks to process (Sync with iOS)
MARKS = [
    "00", "01", "02",
    "10", "11", "12", "13", "14",
    "20", "21", "22", "23", "24",
    "25"
]

def main():
    if not os.path.exists(TARGET_DIR):
        print(f"Creating directory: {TARGET_DIR}")
        os.makedirs(TARGET_DIR)
        
    for mark_id in MARKS:
        merge_filename = f"pin_merge_{mark_id}.svg"
        merge_path = os.path.join(MERGE_DIR, merge_filename)
        output_name = f"map_pin_{mark_id}"
        
        if not os.path.exists(merge_path):
            print(f"⚠️ Warning: Merged source {merge_filename} not found at {merge_path}")
            continue
            
        print(f"Generating Android Bitmap: {output_name}.png from {merge_filename}...")
        
        png_path = os.path.join(TARGET_DIR, f"{output_name}.png")
        
        # rsvg-convert sizes:
        # Base: 40x50 dp
        # xxhdpi (3x): 120x150 px
        # xxxhdpi (4x): 160x200 px (Targeting best quality)
        cmd = f"/opt/homebrew/bin/rsvg-convert -w 160 -h 200 -o {png_path} {merge_path}"
        ret = os.system(cmd)
        
        if ret != 0:
            print(f"❌ Error generating PNG for {output_name}")
        else:
            print(f"✅ Success: {png_path}")
        
    print("\n🎉 Android Static Assets (Bitmap Pins) Integration Complete.")

if __name__ == "__main__":
    main()

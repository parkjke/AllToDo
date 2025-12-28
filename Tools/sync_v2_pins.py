import os
import subprocess
import shutil

# Configuration
PROJECT_ROOT = "/Volumes/Work/AllToDo"
MARK_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/mark") # Using mark folder (Hand design)
SHIELD_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/shield")
MERGE_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/merge")
IOS_ASSET_DIR = os.path.join(PROJECT_ROOT, "AllToDo-iOS/AllToDo/Assets.xcassets")
ANDROID_DRAWABLE_DIR = os.path.join(PROJECT_ROOT, "AllToDo-Android/app/src/main/res/drawable")

# Pin Mappings (ID -> Name)
PINS = {
    "00": "PinCurrent",
    "01": "PinHistory",
    "02": "PinSaved",
    "10": "PinTodoReady",
    "12": "PinTodoDone",
    "13": "PinTodoFail",
    "14": "PinTodoCancel",
    "20": "PinReceiveReady",
    "21": "PinReceiveReject",
    "24": "PinReceiveDone"
}

def ensure_dirs():
    if not os.path.exists(MERGE_DIR):
        os.makedirs(MERGE_DIR)
    if not os.path.exists(MARK_DIR):
        os.makedirs(MARK_DIR)

def merge_svgs():
    print("Merging Marks and Shields...")
    for pid in PINS.keys():
        mark_path = os.path.join(MARK_DIR, f"pin_mark_{pid}.svg")
        
        # Determine shield file based on PID prefix
        if pid.startswith("0"):
            shield_name = "pin_shield_0X.svg"
        elif pid.startswith("1"):
            shield_name = "pin_shield_1X.svg"
        elif pid.startswith("2"):
            shield_name = "pin_shield_2X.svg"
        else:
            shield_name = f"pin_shield_{pid}.svg" # Fallback
            
        shield_path = os.path.join(SHIELD_DIR, shield_name)
        output_path = os.path.join(MERGE_DIR, f"pin_merge_{pid}.svg")

        if not os.path.exists(mark_path):
            print(f"  [Skip] {pid} (Missing mark file)")
            continue
        if not os.path.exists(shield_path):
            print(f"  [Skip] {pid} (Missing shield file)")
            continue

        try:
            with open(shield_path, 'r') as f:
                shield_content = f.read()
            with open(mark_path, 'r') as f:
                mark_content = f.read()

            # Simple SVG merging: inject mark content before the closing </svg> of shield
            # This assumes standard SVG structure. A more robust parser might be needed for complex cases.
            # We strip the outer <svg> tags from mark_content for embedding
            
            mark_body = mark_content
            # Remove XML declaration if present
            if "<?xml" in mark_body:
                mark_body = mark_body.split("?>", 1)[1]
            
            # Remove <svg ...> and </svg>
            start_tag_end = mark_body.find(">")
            if (start_tag_end != -1):
                 mark_body = mark_body[start_tag_end+1:]
            
            mark_body = mark_body.replace("</svg>", "")
            
            # center/scale mark if needed is handled in the mark SVG itself usually, 
            # or we wrap it in a group.
            # For this task, we assume mark SVGs are designed to overlay correctly.
            
            final_svg = shield_content.replace("</svg>", f"\n<g id='mark'>{mark_body}</g>\n</svg>")
            
            with open(output_path, 'w') as f:
                f.write(final_svg)
            
            print(f"  [Merge] Created: pin_merge_{pid}.svg")
            
        except Exception as e:
            print(f"  [Error] Failed to merge {pid}: {e}")

def sync_ios():
    print("Syncing to iOS Assets...")
    for pid, name in PINS.items():
        merge_path = os.path.join(MERGE_DIR, f"pin_merge_{pid}.svg")
        if not os.path.exists(merge_path):
            continue
            
        asset_folder = os.path.join(IOS_ASSET_DIR, f"{name}.imageset")
        if not os.path.exists(asset_folder):
            os.makedirs(asset_folder)
            
        dest_path = os.path.join(asset_folder, f"{name}.svg")
        shutil.copy(merge_path, dest_path)
        
        # Create/Update Contents.json
        contents_json = """{
  "images" : [
    {
      "filename" : "%s.svg",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}""" % name
        with open(os.path.join(asset_folder, "Contents.json"), 'w') as f:
            f.write(contents_json)
        
        print(f"  [iOS] Updated: {name}")

def sync_android():
    print("Syncing to Android Drawables...")
    # For Android, we need XML vector drawables. 
    # Since we can't easily convert complex SVG to Vector XML automatically with python standard libs without tools,
    # and the user task involves heavily customized XMLs for complex marks,
    # we will SKIP automatic overwriting of the hand-crafted XMLs for now, 
    # OR we could just copy SVGs if we were using coil-svg, but we are using VectorDrawables.
    
    # However, the previous tool DID try to sync. 
    # Given the high complexity of v3.9 marks (gradients, masks, etc), 
    # we rely on the manual XML edits I've been doing in the 'write_to_file' steps for Android.
    # So this script will mainly focus on iOS sync which uses the SVGs directly.
    print("  [Android] Skipping auto-sync (Manual XML editing preferred for complex v3 designs)")

if __name__ == "__main__":
    ensure_dirs()
    merge_svgs()
    # sync_ios() # Paused for design review
    # sync_android() # Paused for design review

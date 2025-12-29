import os
import shutil
import subprocess

# Configuration
PROJECT_ROOT = "/Volumes/Work/AllToDo"
MARK_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/mark")
SHIELD_DIR = os.path.join(PROJECT_ROOT, "Icons/map_pin_1/shield")
IOS_ASSET_DIR = os.path.join(PROJECT_ROOT, "AllToDo-iOS/AllToDo/Assets.xcassets")
ANDROID_DRAWABLE_DIR = os.path.join(PROJECT_ROOT, "AllToDo-Android/app/src/main/res/drawable")
CONVERT_SCRIPT = os.path.join(PROJECT_ROOT, "Tools/convert_svg_xml.py")

def sync_ios_components():
    print("Syncing iOS Components (Shields & Marks)...")
    
    # helper to create imageset
    def create_imageset(name, src_path, dest_parent):
        imageset_dir = os.path.join(dest_parent, f"{name}.imageset")
        if not os.path.exists(imageset_dir):
            os.makedirs(imageset_dir)
            
        dest_file = os.path.join(imageset_dir, f"{name}.svg")
        shutil.copy(src_path, dest_file)
        
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
        with open(os.path.join(imageset_dir, "Contents.json"), 'w') as f:
            f.write(contents_json)

    # 1. Sync Shields
    dest_shields = os.path.join(IOS_ASSET_DIR, "Components") 
    # Create nested folder structure manually or flat list in Components?
    # Let's put them in 'Components' grouping visually in Xcode if possible, but physically flat or nested.
    # To keep it simple, we just put them in Assets.xcassets/Components/Shields
    
    shields_target = os.path.join(IOS_ASSET_DIR, "Components/Shields")
    if not os.path.exists(shields_target):
        os.makedirs(shields_target)
        
    for f in os.listdir(SHIELD_DIR):
        if f.endswith(".svg"):
            name = os.path.splitext(f)[0]
            create_imageset(name, os.path.join(SHIELD_DIR, f), shields_target)
            print(f"  [iOS] Shield: {name}")

    # 2. Sync Marks
    marks_target = os.path.join(IOS_ASSET_DIR, "Components/Marks")
    if not os.path.exists(marks_target):
        os.makedirs(marks_target)
        
    for f in os.listdir(MARK_DIR):
        if f.endswith(".svg"):
            name = os.path.splitext(f)[0]
            create_imageset(name, os.path.join(MARK_DIR, f), marks_target)
            print(f"  [iOS] Mark: {name}")


def sync_android_components():
    print("Syncing Android Components (Shields & Marks)...")
    if not os.path.exists(CONVERT_SCRIPT):
        print("  [Error] Convert script not found!")
        return

    # Helper
    def convert_and_copy(src_path, dest_name):
        dest_path = os.path.join(ANDROID_DRAWABLE_DIR, dest_name)
        # Call conversion script
        subprocess.run(["python3", CONVERT_SCRIPT, src_path, dest_path], check=True)

    # 1. Sync Shields
    for f in os.listdir(SHIELD_DIR):
        if f.endswith(".svg"):
            name = os.path.splitext(f)[0].lower() # Android resources must be lowercase
            convert_and_copy(os.path.join(SHIELD_DIR, f), f"{name}.xml")
            print(f"  [Android] Shield: {name}")

    # 2. Sync Marks
    for f in os.listdir(MARK_DIR):
        if f.endswith(".svg"):
            name = os.path.splitext(f)[0].lower()
            convert_and_copy(os.path.join(MARK_DIR, f), f"{name}.xml")
            print(f"  [Android] Mark: {name}")

if __name__ == "__main__":
    sync_ios_components()
    sync_android_components()

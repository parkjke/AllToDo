
import os
import shutil
import json

ICONS_DIR = "/Volumes/Work/AllToDo/Icons/Generated"
ASSETS_DIR = "/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Assets.xcassets"

def to_camel_case(snake_str):
    components = snake_str.split('_')
    # Capitalize all components
    return "".join(x.title() for x in components)

def integrate():
    if not os.path.exists(ICONS_DIR):
        print("Error: Icons dir not found")
        return

    files = [f for f in os.listdir(ICONS_DIR) if f.endswith(".svg")]
    
    for filename in files:
        # pin_todo_ready.svg -> PinTodoReady
        name_no_ext = os.path.splitext(filename)[0]
        imageset_name = to_camel_case(name_no_ext)
        
        imageset_path = os.path.join(ASSETS_DIR, f"{imageset_name}.imageset")
        if not os.path.exists(imageset_path):
            os.makedirs(imageset_path)
            
        # Copy SVG
        src = os.path.join(ICONS_DIR, filename)
        dst = os.path.join(imageset_path, filename)
        shutil.copy2(src, dst)
        
        # Write Contents.json
        contents = {
            "images" : [
                {
                    "filename" : filename,
                    "idiom" : "universal"
                }
            ],
            "info" : {
                "author" : "xcode",
                "version" : 1
            },
            "properties" : {
                "preserves-vector-representation" : True
            }
        }
        
        with open(os.path.join(imageset_path, "Contents.json"), "w") as f:
            json.dump(contents, f, indent=2)
            
        print(f"Integrated iOS Asset: {imageset_name} ({filename})")

if __name__ == "__main__":
    integrate()

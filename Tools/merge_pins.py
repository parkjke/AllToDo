import os
import re

BASE_DIR = "/Volumes/Work/AllToDo/Icons/map_pin_1"
MARK_DIR = os.path.join(BASE_DIR, "mark")
SHIELD_DIR = os.path.join(BASE_DIR, "shield")
MERGE_DIR = os.path.join(BASE_DIR, "merge")

GROUPS = {
    "0": ["00", "01", "02", "03"],
    "1": ["10", "11", "12", "13", "14"],
    "2": ["20", "21", "22", "23", "24", "25"]
}

def read_svg_content(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    # Extract everything inside <svg ...> ... </svg>
    match = re.search(r'<svg[^>]*>(.*)</svg>', content, re.DOTALL)
    if match:
        return match.group(1).strip()
    return ""

def transform_mark(mark_id, mark_content):
    # Pin 00, 01, 02, 10-14, 20-24 are custom/positioned -> No X translation needed
    if mark_id in ["00", "01", "02", "03", "10", "11", "12", "13", "14", "20", "21", "22", "23", "24", "25"]:
        transform_attr = ""
        # Exception for foot 01: shift up/left relative to current SVG
        if mark_id == "01":
            transform_attr = ' transform="translate(0, 10)"'
    else:
        # Default for simple icons: center them
        transform_attr = ' transform="translate(15, 18)"'
    
    return f'  <g id="mark_{mark_id}"{transform_attr}>\n{mark_content}\n  </g>'

def create_merged_svg(mark_id, shield_suffix):
    mark_path = os.path.join(MARK_DIR, f"pin_mark_{mark_id}.svg")
    shield_path = os.path.join(SHIELD_DIR, f"pin_shield_{shield_suffix}.svg")
    output_path = os.path.join(MERGE_DIR, f"pin_merge_{mark_id}.svg")

    if not os.path.exists(mark_path):
        print(f"Mark {mark_id} not found, skipping.")
        return

    shield_content = read_svg_content(shield_path)
    mark_content = read_svg_content(mark_path)
    
    transformed_mark = transform_mark(mark_id, mark_content)

    # Shield is 100x125. We use that as the base viewBox.
    svg_template = f"""<svg width="200" height="250" viewBox="0 0 100 125" xmlns="http://www.w3.org/2000/svg">
{shield_content}
  <!-- Mark {mark_id} -->
{transformed_mark}
</svg>"""

    with open(output_path, 'w') as f:
        f.write(svg_template)
    print(f"Created {output_path}")

def main():
    if not os.path.exists(MERGE_DIR):
        os.makedirs(MERGE_DIR)

    for group_prefix, mark_ids in GROUPS.items():
        shield_suffix = f"{group_prefix}X"
        for mark_id in mark_ids:
            create_merged_svg(mark_id, shield_suffix)

if __name__ == "__main__":
    main()

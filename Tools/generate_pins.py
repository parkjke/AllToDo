
import os
import re

# Output Directory
OUTPUT_DIR = "/Volumes/Work/AllToDo/Icons/Generated"
if not os.path.exists(OUTPUT_DIR):
    os.makedirs(OUTPUT_DIR)

# Template (Modified from map-pin-gemini.svg)
# We replace colors and the inner icon group
SVG_TEMPLATE = """<svg width="100" height="125" viewBox="0 0 100 125" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="mainGradient" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" style="stop-color:{COLOR_TOP};stop-opacity:1" />
      <stop offset="100%" style="stop-color:{COLOR_BOT};stop-opacity:1" />
    </linearGradient>
    <filter id="shadowBottom" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="3" stdDeviation="2" flood-color="#000000" flood-opacity="0.4"/>
    </filter>
  </defs>

  <path d="M10,15 Q50,5 90,15 Q100,40 95,65 Q75,80 50,120 Q25,80 5,65 Q0,40 10,15 Z" 
        fill="url(#mainGradient)" filter="url(#shadowBottom)"/>

  <g transform="translate(50, 45) scale(0.6) translate(-50, -50)">
      {ICON_PATH}
  </g>
</svg>
"""

# Color Definitions (Gradient Top, Bottom)
COLORS = {
    "green": ("#34C759", "#008037"),
    "red":   ("#FF3B30", "#B00020"),
    "blue":  ("#007AFF", "#0040DD")
}

# Icon Paths (Defined for 100x100 box centered at 50,50)
ICONS = {
    "star": '<polygon points="50,5 61,35 95,35 68,55 79,85 50,70 21,85 32,55 5,35 39,35" fill="#FFFFFF"/>',
    
    "flag": '<path d="M30,10 L30,90 M30,10 L80,30 L30,50" stroke="#FFFFFF" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
    
    "check": '<path d="M20,50 L40,70 L80,30" stroke="#FFFFFF" stroke-width="12" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
    
    "dash": '<rect x="20" y="45" width="60" height="10" rx="5" fill="#FFFFFF"/>',
    
    "x": '<path d="M25,25 L75,75 M75,25 L25,75" stroke="#FFFFFF" stroke-width="12" fill="none" stroke-linecap="round"/>',
    
    "crosshair": '''
        <circle cx="50" cy="50" r="30" stroke="#FFFFFF" stroke-width="6" fill="none"/>
        <line x1="50" y1="10" x2="50" y2="90" stroke="#FFFFFF" stroke-width="6"/>
        <line x1="10" y1="50" x2="90" y2="50" stroke="#FFFFFF" stroke-width="6"/>
    ''',
    
    "lightning": '<path d="M55,10 L25,60 L50,60 L40,90 L75,40 L50,40 Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2" stroke-linejoin="round"/>',
    
    "boxed_check": '''
        <rect x="15" y="15" width="70" height="70" rx="10" stroke="#FFFFFF" stroke-width="6" fill="none"/>
        <path d="M30,50 L45,65 L70,35" stroke="#FFFFFF" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
    ''',
    
    "hand": '''
        <path d="M35,90 L35,50 L25,50 L25,30 Q25,10 50,10 Q75,10 75,30 L75,50 L65,50 L65,90 Z" fill="#FFFFFF"/>
        <path d="M25,30 L25,50 M35,50 L35,30 M50,10 L50,40 M65,50 L65,30 M75,50 L75,30" stroke="#FFFFFF" stroke-width="4" fill="none"/>
        <path d="M20,20 L80,80 M80,20 L20,80" stroke="#FF0000" stroke-width="8" stroke-opacity="0.0"/> 
    ''' 
    # Hand is hard, using a "Stop Hand" metaphor: Just a simple open palm shape.
    # Refined Hand:
    # <path d="M30,90 L30,60 L20,60 L20,40 Q20,20 35,20 L40,20 L40,15 Q40,5 50,5 Q60,5 60,15 L60,20 L65,20 Q80,20 80,40 L80,60 L70,60 L70,90 Z" fill="white"/>
}
# Replacing Hand with a clearer "Stop" Icon (Octagon with Hand inside or just Stop Text? Ill use a simple Hand Print)
ICONS["hand"] = '<path d="M35,95 L35,60 L25,60 L25,35 Q25,15 35,15 L40,15 L40,10 Q40,0 50,0 Q60,0 60,10 L60,15 L65,15 Q75,15 75,35 L75,60 L65,60 L65,95 Z" fill="#FFFFFF"/>'

# Refined Flag (Filled)
ICONS["flag"] = '<path d="M30,10 L30,90 M30,12 L80,32 L30,52 Z" stroke="#FFFFFF" stroke-width="6" fill="#FFFFFF" stroke-linejoin="round"/>'


# Pin Definitions: (Filename, Color, Icon)
PINS = [
    # ToDo (Green)
    ("pin_todo_ready.svg",  "green", "flag"),
    ("pin_todo_done.svg",   "green", "check"),
    ("pin_todo_cancel.svg", "green", "dash"),
    ("pin_todo_fail.svg",   "green", "x"),
    
    # History/Current (Red)
    ("pin_history.svg",     "red",   "star"),
    ("pin_current.svg",     "red",   "crosshair"),
    
    # Receive (Blue)
    ("pin_receive_ready.svg",  "blue", "lightning"),
    ("pin_receive_done.svg",   "blue", "boxed_check"),
    ("pin_receive_reject.svg", "blue", "hand"),
]

def generate():
    for filename, color_name, icon_name in PINS:
        color_top, color_bot = COLORS[color_name]
        icon_svg = ICONS[icon_name]
        
        svg_content = SVG_TEMPLATE.format(
            COLOR_TOP=color_top,
            COLOR_BOT=color_bot,
            ICON_PATH=icon_svg
        )
        
        filepath = os.path.join(OUTPUT_DIR, filename)
        with open(filepath, "w") as f:
            f.write(svg_content)
        print(f"Generated: {filepath}")

if __name__ == "__main__":
    generate()

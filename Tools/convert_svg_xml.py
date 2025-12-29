import os
import re
import sys
from xml.dom import minidom

def svg_to_android_vector(svg_content, width="100dp", height="125dp", viewport_width="100", viewport_height="125"):
    # Extract paths
    paths = re.findall(r'<path[^>]*d="([^"]*)"[^>]*fill="([^"]*)"[^>]*/>', svg_content)
    # If standard regex fail, try more lenient or use a parser if needed. 
    # For now, simplistic regex for standard SVGs we generated.
    
    # Actually, a better approach for our specific SVGs is to check if it has gradients vs solid fills
    
    # 1. Header
    out = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="{width}"
    android:height="{height}"
    android:viewportWidth="{viewport_width}"
    android:viewportHeight="{viewport_height}">
'''
    
    # Very simple parser to extract groups and paths
    # Note: rigorous SVG to VectorDrawable is hard. We implement a specific subset for our pins.
    
    # We will try to preserve the body logic by regexing the content inside <svg> tags
    try:
        body_match = re.search(r'<svg[^>]*>(.*)</svg>', svg_content, re.DOTALL)
        if body_match:
            body = body_match.group(1)
            
            # Convert generic SVG tags to Android XML tags
            # <path ... d="..." fill="..." /> -> <path android:pathData="..." android:fillColor="..." />
            
            # Simple replacements for common attributes
            body = re.sub(r'\bd="', 'android:pathData="', body)
            body = re.sub(r'\bfill="', 'android:fillColor="', body)
            body = re.sub(r'\bstroke="', 'android:strokeColor="', body)
            body = re.sub(r'\bstroke-width="', 'android:strokeWidth="', body)
            body = re.sub(r'\bstroke-linecap="', 'android:strokeLineCap="', body)
            body = re.sub(r'\bstroke-linejoin="', 'android:strokeLineJoin="', body)
            body = re.sub(r'\bstroke-opacity="', 'android:strokeAlpha="', body)
            body = re.sub(r'\bfill-opacity="', 'android:fillAlpha="', body)
            
            # Transforms needs manual handling usually, Android uses group for transforms
            # <g transform="..."> -> <group ...>
            
            # Identify <g> tags and attributes
            # This is complex to do with regex. 
            # Given user constraint "no accidents", let's use a simpler strategy:
            # We assume our SVGs are clean (we made them).
            
            # Handle transforms: translate(x, y) -> android:translateX="x" android:translateY="y"
            def replace_transform(match):
                tf = match.group(1)
                res = ""
                # translate
                m_tr = re.search(r'translate\(([^,]+)[,\s]+([^)]+)\)', tf)
                if m_tr:
                    res += f' android:translateX="{m_tr.group(1).strip()}" android:translateY="{m_tr.group(2).strip()}"'
                
                # scale
                m_sc = re.search(r'scale\(([^)]+)\)', tf)
                if m_sc:
                   s = m_sc.group(1).strip()
                   if "," in s:
                       sx, sy = s.split(",")
                       res += f' android:scaleX="{sx.strip()}" android:scaleY="{sy.strip()}"'
                   else:
                       res += f' android:scaleX="{s}" android:scaleY="{s}"'
                       
                # rotation
                m_rot = re.search(r'rotate\(([^)]+)\)', tf)
                if m_rot:
                    res += f' android:rotation="{m_rot.group(1).strip()}"'
                    
                return res

            body = re.sub(r'\btransform="([^"]*)"', replace_transform, body)
            
            # g -> group
            body = body.replace("<g", "<group").replace("</g>", "</group>")
            
            # circle -> path (Android Vector doesn't support circle directly, need path conversion)
            # circle cx="50" cy="50" r="10" -> pathData=" M50,40 A10,10 0 1,1 50,60 A10,10 0 1,1 50,40 "
            def replace_circle(match):
                # cx, cy, r, attr
                cx = float(match.group(1))
                cy = float(match.group(2))
                r = float(match.group(3))
                rest = match.group(4)
                
                # Create arc path for circle
                d = f"M {cx-r},{cy} a {r},{r} 0 1,0 {r*2},0 a {r},{r} 0 1,0 -{r*2},0"
                
                # convert rest attributes
                rest = re.sub(r'\bfill="', 'android:fillColor="', rest)
                rest = re.sub(r'\bstroke="', 'android:strokeColor="', rest)
                rest = re.sub(r'\bstroke-width="', 'android:strokeWidth="', rest)
                
                return f'<path android:pathData="{d}" {rest} />'
            
            # Regex for circle: <circle cx=".." cy=".." r=".." ... />
            # Note: simplistic, assumes order or attributes logic.
            # Real parser is better, but dependency free script is preferred.
            
            # Let's search specifically for our known formats
            # <circle cx="50" cy="35" r="9" fill="white" />
            body = re.sub(r'<circle\s+cx="([^"]+)"\s+cy="([^"]+)"\s+r="([^"]+)"([^>]*)/>', replace_circle, body)
            
            out += body
    except Exception as e:
        out += f"<!-- Error parsing SVG content: {e} -->"

    out += "\n</vector>"
    return out

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 convert_svg_xml.py <input_svg> <output_xml>")
        sys.exit(1)
        
    in_path = sys.argv[1]
    out_path = sys.argv[2]
    
    with open(in_path, 'r') as f:
        svg_content = f.read()
        
    xml_content = svg_to_android_vector(svg_content)
    
    with open(out_path, 'w') as f:
        f.write(xml_content)
    
    print(f"Converted {in_path} -> {out_path}")

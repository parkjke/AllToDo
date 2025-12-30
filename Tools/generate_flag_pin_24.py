import math

def generate_wavy_checkers(filename):
    """
    Generates an SVG file with a 6x3 checkerboard pattern distorted by a sine wave
    to simulate a fluttering flag effect.
    """
    rows = 3
    cols = 6
    cell_size = 12
    
    # Wave parameters
    amplitude = 4.0
    frequency = 0.08 # Radians per pixel approximately
    
    # Offset to center roughly at (50, 42)
    start_x = 14
    start_y = 26
    
    # We will generate a mesh of points
    points = []
    
    for r in range(rows + 1):
        row_points = []
        for c in range(cols + 1):
            x = start_x + (c * cell_size)
            # Apply sine wave to Y based on X
            y_base = start_y + (r * cell_size)
            y_offset = amplitude * math.sin((x - start_x) * frequency)
            
            y = y_base + y_offset
            row_points.append((x, y))
        points.append(row_points)
        
    svg_content = '<svg width="100" height="90" viewBox="0 0 100 90" xmlns="http://www.w3.org/2000/svg">\n'
    svg_content += '  <!-- Generated Waving Flag Pattern -->\n'
    svg_content += '  <g stroke="#000000" stroke-width="0.5" stroke-linejoin="round">\n'
    
    for r in range(rows):
        for c in range(cols):
            # Define Quad points: TopLeft, TopRight, BottomRight, BottomLeft
            p1 = points[r][c]
            p2 = points[r][c+1]
            p3 = points[r+1][c+1]
            p4 = points[r+1][c]
            
            # Determine color (Alternating)
            is_white = (r + c) % 2 == 1
            fill_color = "#FFFFFF" if is_white else "#000000"
            
            path_d = f"M {p1[0]:.2f},{p1[1]:.2f} L {p2[0]:.2f},{p2[1]:.2f} L {p3[0]:.2f},{p3[1]:.2f} L {p4[0]:.2f},{p4[1]:.2f} Z"
            
            svg_content += f'    <path d="{path_d}" fill="{fill_color}" />\n'
            
    svg_content += '  </g>\n'
    svg_content += '</svg>'
    
    with open(filename, "w") as f:
        f.write(svg_content)
    
    print(f"Generated {filename}")

if __name__ == "__main__":
    generate_wavy_checkers("/Volumes/Work/AllToDo/Icons/map_pin_1/mark/pin_mark_24.svg")

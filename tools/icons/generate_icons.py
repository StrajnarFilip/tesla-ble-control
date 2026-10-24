#!/usr/bin/env python3
"""Generates the app's action icons as Android vector drawables, plus an SVG preview sheet.

    python3 tools/icons/generate_icons.py app/src/main/res/drawable tools/icons/build/preview.png

Each icon is a list of parts: (pathData, kind, alpha). Kinds: "s" stroke and "f" fill in the main
colour; "S" (bold stroke) and "F" (fill) for the accent, the part that moves or changes such as a
trunk lid; "m" and "mf" for muted parts that stay put, such as the rest of the car. Accent and muted
parts use marker colours that TwoToneIcon (app) swaps for solid theme colours, so nothing relies on
transparency (overlapping transparent strokes look patchy); with a plain Icon tint everything takes
the tint.
Wide pictures (the car) get a cropped view box so they draw large.
"""
import os
import subprocess
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "drawable"
SHEET = sys.argv[2] if len(sys.argv) > 2 else "icons.png"
FAINT = 0.4
CAR = 0.65  # the car body around an accented part
W = 1.7  # stroke width in a 24-unit viewport
BOLD = 2.4  # the part that moves
BASE_COLOR = "#FF000000"
ACCENT_COLOR = "#FFFF00FF"  # markers, replaced in the app; keep in sync with TwoToneIcon.kt
MUTED_COLOR = "#FF00FFFF"

# Cropped view boxes (x, y, width, height) for wide pictures, so they fill a wide slot.
CAR_BOX = (1.6, 4.4, 21.75, 14.5)

# --- Car side view, front on the left, wheels resting on y=17.5 ---
FRONT = "M3.4,16.5 L2.9,13.4 Q2.9,12.3 4.1,12.1"
HOOD = "M4.1,12.1 L8.6,11.4"
CABIN = "M8.6,11.4 L11.2,8.2 Q11.7,7.6 12.6,7.6 L15.7,7.6 Q16.6,7.6 17.2,8.3 L19.5,11"
TRUNK = "M19.5,11 L21,11.4 Q21.4,11.6 21.4,12.3"
REAR = "M21.4,12.3 L21.4,15.6 Q21.4,16.5 20.5,16.5 L19.7,16.5 A2.1,2.1 0 0,0 15.5,16.5 L8.5,16.5 A2.1,2.1 0 0,0 4.3,16.5 L3.4,16.5"
WHEELS = "M17.6,16.5 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 M6.4,16.5 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0"
# Side windows inside the cabin outline.
WINDOWS = "M10.4,10.8 L12.3,8.9 L13.7,8.9 L13.7,10.8 Z M15,8.9 L16.2,8.9 L17.6,10.8 L15,10.8 Z"


def car(bold=None, skip=()):
    """The car as one muted path, with the named part as the accent."""
    body = [d for name, d in [("front", FRONT), ("hood", HOOD), ("cabin", CABIN), ("trunk", TRUNK), ("rear", REAR), ("wheels", WHEELS)]
            if name != bold and name not in skip]
    parts = [(" ".join(body), "m", 1.0)]
    if bold:
        parts.append(({"hood": HOOD, "trunk": TRUNK, "cabin": CABIN}[bold], "S", 1.0))
    return parts


ALL = ("front", "hood", "cabin", "trunk", "rear", "wheels")


# A car seat from the side, facing left.
SEAT = "M5.5,3.5 Q4.3,3.5 4.5,4.8 L6,15.2 Q6.2,16.5 7.5,16.5 L15.5,16.5 Q16.8,16.5 16.8,15.2 L16.8,14 Q16.8,12.7 15.5,12.7 L8.8,12.7 L7.6,4.6 Q7.4,3.5 6.4,3.5 Z M8,16.5 L8,20 M14.5,16.5 L14.5,20"
HEAT_WAVES = ["M11.5,10.5 Q10.6,9.2 11.5,7.9 Q12.4,6.6 11.5,5.3",
              "M14.5,10.5 Q13.6,9.2 14.5,7.9 Q15.4,6.6 14.5,5.3",
              "M17.5,10.5 Q16.6,9.2 17.5,7.9 Q18.4,6.6 17.5,5.3"]
AIR_LINES = ["M11.5,5.5 L15,5.5", "M11.5,8 L17,8", "M11.5,10.5 L19,10.5"]


def seat(level, marks):
    parts = [(SEAT, "s" if level else "m", 1.0)]
    parts += [(m, "s", 1.0) for m in marks[:level]]
    return parts


def _ring(cx, cy, r):
    return f"M{cx},{cy} m-{r},0 a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 -{2*r},0"


def snowflake():
    import math
    lines, branches = [], []
    for k in range(3):
        a = math.radians(90 + 60 * k)
        dx, dy = 8.5 * math.cos(a), 8.5 * math.sin(a)
        lines.append(f"M{12-dx:.2f},{12-dy:.2f} L{12+dx:.2f},{12+dy:.2f}")
        for sign in (1, -1):
            tipx, tipy = 12 + sign * dx * 0.62, 12 + sign * dy * 0.62
            for side in (1, -1):
                b = a + side * math.radians(40) + (math.pi if sign < 0 else 0)
                branches.append(f"M{tipx:.2f},{tipy:.2f} L{tipx + 2.8*math.cos(b):.2f},{tipy + 2.8*math.sin(b):.2f}")
    return [(" ".join(lines), "s", 1.0), (" ".join(branches), "s", 1.0)]


def fan():
    import math
    blades = []
    for k in range(3):
        a = math.radians(-90 + 120 * k)
        tip = (12 + 8.5 * math.cos(a), 12 + 8.5 * math.sin(a))
        c1 = (12 + 8 * math.cos(a - 0.65), 12 + 8 * math.sin(a - 0.65))
        c2 = (12 + 3.5 * math.cos(a + 0.6), 12 + 3.5 * math.sin(a + 0.6))
        blades.append(f"M12,12 Q{c1[0]:.2f},{c1[1]:.2f} {tip[0]:.2f},{tip[1]:.2f} Q{c2[0]:.2f},{c2[1]:.2f} 12,12 Z")
    return [(" ".join(blades), "s", 1.0), (_ring(12, 12, 1.6), "f", 1.0)]


def thermometer(level_y):
    return [
        ("M10.3,14.4 L10.3,5 A1.7,1.7 0 0,1 13.7,5 L13.7,14.4 A3.6,3.6 0 1,1 10.3,14.4 Z", "s", 1.0),
        (f"M12,17.4 L12,{level_y}", "S", 1.0),
        (_ring(12, 17.4, 1.7), "F", 1.0),
        ("M16,6.6 L17.6,6.6 M16,10.2 L17.6,10.2 M16,13.2 L17.6,13.2", "m", 1.0),
    ]


def sun():
    import math
    rays = []
    for k in range(8):
        a = math.radians(45 * k)
        rays.append(f"M{12+6.5*math.cos(a):.2f},{12+6.5*math.sin(a):.2f} L{12+8.8*math.cos(a):.2f},{12+8.8*math.sin(a):.2f}")
    return [(_ring(12, 12, 4), "s", 1.0), (" ".join(rays), "s", 1.0)]


def steering_wheel(cy, r):
    return [
        (_ring(12, cy, r), "s", 1.0),
        (f"M{12-r},{cy} L10.6,{cy} M13.4,{cy} L{12+r},{cy} M12,{cy+1.4} L12,{cy+r}", "s", 1.0),
        (_ring(12, cy, 1.4), "f", 1.0),
    ]


def slash():
    return [("M4,4 L20,20", "s", 1.0)]


ICONS = {
    # Locks: the shackle closes into the body or swings open.
    "ic_action_locked": [
        ("M6.5,11 L17.5,11 Q18.5,11 18.5,12 L18.5,19 Q18.5,20 17.5,20 L6.5,20 Q5.5,20 5.5,19 L5.5,12 Q5.5,11 6.5,11 Z", "s", 1.0),
        ("M8.5,11 L8.5,8 A3.5,3.5 0 0,1 15.5,8 L15.5,11", "s", 1.0),
        ("M12,14.6 L12,16.4", "s", 1.0),
    ],
    "ic_action_unlocked": [
        ("M6.5,11 L17.5,11 Q18.5,11 18.5,12 L18.5,19 Q18.5,20 17.5,20 L6.5,20 Q5.5,20 5.5,19 L5.5,12 Q5.5,11 6.5,11 Z", "s", 1.0),
        ("M8.5,11 L8.5,7 A3.5,3.5 0 0,1 15.2,5.6", "s", 1.0),
        ("M12,14.6 L12,16.4", "s", 1.0),
    ],
    # Front trunk: the hood is bold; open lifts it at the front.
    "ic_action_frunk_closed": car(bold="hood"),
    "ic_action_frunk_open": car(skip=("hood",)) + [("M8.6,11.4 L3.8,7.4", "S", 1.0)],
    # Rear trunk: the lid is bold; open raises it behind the roof.
    "ic_action_trunk_closed": car(bold="trunk"),
    "ic_action_trunk_open": car(skip=("trunk",)) + [("M19.5,11 L22.2,6.6", "S", 1.0)],
    # Windows: a door seen close up; the glass fills the frame or is lowered, leaving a gap.
    "ic_action_windows_closed": [
        ("M3.5,12 L8,4.5 L20.5,4.5 L20.5,12 M3.5,12 L3.5,19.5 L20.5,19.5 L20.5,12 Z", "m", 1.0),
        ("M6.2,11 L8.9,6.2 L19,6.2 L19,11 Z", "F", 1.0),
        ("M15,15 L17.5,15", "m", 1.0),
    ],
    "ic_action_windows_vented": [
        ("M3.5,12 L8,4.5 L20.5,4.5 L20.5,12 M3.5,12 L3.5,19.5 L20.5,19.5 L20.5,12 Z", "m", 1.0),
        ("M6.2,11 L7.7,8.6 L19,8.6 L19,11 Z", "F", 1.0),
        ("M10.5,6.6 L13.5,6.6 M15,6.6 L17,6.6", "s", 1.0),
        ("M15,15 L17.5,15", "m", 1.0),
    ],
    # Sunroof: the roof panel is bold; open lifts it and slides it back.
    "ic_action_sunroof_closed": car() + [("M12.6,7.6 L15.7,7.6", "S", 1.0)],
    "ic_action_sunroof_open": car() + [("M14.2,5.4 L18.4,6.1", "S", 1.0)],
    # Cybertruck bed: covered, or open with the cover rolled up at the cab.
    "ic_action_tonneau_closed": [
        ("M2.5,15.5 L2.5,12.8 L11,7.5 L21.5,11.6 L21.5,15.5", "m", 1.0),
        ("M2.5,15.5 L4.6,15.5 M8.6,15.5 L15.4,15.5 M19.4,15.5 L21.5,15.5", "m", 1.0),
        ("M6.6,15.5 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M17.4,15.5 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0", "m", 1.0),
        ("M13.6,8.5 L21.5,11.6", "S", 1.0),
    ],
    "ic_action_tonneau_open": [
        ("M2.5,15.5 L2.5,12.8 L11,7.5 L13.6,8.5", "m", 1.0),
        ("M13.6,11.6 L21.5,11.6 L21.5,15.5", "m", 1.0),
        ("M2.5,15.5 L4.6,15.5 M8.6,15.5 L15.4,15.5 M19.4,15.5 L21.5,15.5", "m", 1.0),
        ("M6.6,15.5 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M17.4,15.5 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0", "m", 1.0),
        ("M13.6,8.5 L13.6,11.6", "m", 1.0),
        ("M14.9,9.6 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0", "S", 1.0),
    ],
    # Charge port: a small door over the socket, or swung open showing the pins.
    "ic_action_port_closed": [
        ("M7,5 L17,5 Q19,5 19,7 L19,17 Q19,19 17,19 L7,19 Q5,19 5,17 L5,7 Q5,5 7,5 Z", "s", 1.0),
        ("M12.8,8.2 L10,12.4 L13.6,12.4 L11.2,16.2", "s", 1.0),
    ],
    "ic_action_port_open": [
        ("M2.5,6.5 L5.5,4.8 L5.5,19.2 L2.5,17.5 Z", "m", 1.0),
        ("M14,12 m-6,0 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0", "S", 1.0),
        ("M12,10 m-0.9,0 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 M16,10 m-0.9,0 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 M12,14 m-0.9,0 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 M16,14 m-0.9,0 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0", "f", 1.0),
    ],
    # Headlight with beams.
    "ic_action_flash": [
        ("M10,6 Q4,6 4,12 Q4,18 10,18 Z", "s", 1.0),
        ("M13.5,7.5 L20,6 M13.5,12 L20.5,12 M13.5,16.5 L20,18", "s", 1.0),
    ],
    # Horn with sound waves.
    "ic_action_honk": [
        ("M3.5,9.5 L7,9.5 L12,5.5 L12,18.5 L7,14.5 L3.5,14.5 Z", "s", 1.0),
        ("M15.2,9.2 A4,4 0 0,1 15.2,14.8 M17.8,6.6 A7.6,7.6 0 0,1 17.8,17.4", "s", 1.0),
    ],
    # Garage with a door and a signal.
    "ic_action_homelink": [
        ("M3,20 L3,9.5 L10.5,4.5 L15,7.5", "s", 1.0),
        ("M18,13 L18,20", "s", 1.0),
        ("M6.5,20 L6.5,12 L14.5,12 L14.5,20 M6.5,15 L14.5,15 M6.5,17.8 L14.5,17.8", "s", 1.0),
        ("M17,6.5 A2.5,2.5 0 0,1 19.5,9 M17,3.5 A5.5,5.5 0 0,1 22.5,9", "s", 1.0),
    ],
    # A start button.
    "ic_action_remote_start": [
        ("M8.3,6.8 A7,7 0 1,0 15.7,6.8", "s", 1.0),
        ("M12,3.5 L12,11", "s", 1.0),
    ],
    # Sun rising over the horizon.
    "ic_action_wake": [
        ("M3,17.5 L21,17.5", "s", 1.0),
        ("M7.5,17.5 A4.5,4.5 0 0,1 16.5,17.5", "s", 1.0),
        ("M12,5 L12,8 M5.6,8.6 L7.6,10.6 M18.4,8.6 L16.4,10.6 M2.8,13.4 L5.2,13.8 M21.2,13.4 L18.8,13.8", "s", 1.0),
    ],
    # Messages both ways.
    "ic_action_ping": [
        ("M4,9 L19,9 M15.5,5.5 L19,9 L15.5,12.5", "s", 1.0),
        ("M20,15 L5,15 M8.5,11.5 L5,15 L8.5,18.5", "s", 1.0),
    ],
    "ic_action_previous": [
        ("M6,5.5 L6,18.5", "s", 1.0),
        ("M18.5,6 L8.5,12 L18.5,18 Z", "f", 1.0),
    ],
    "ic_action_next": [
        ("M18,5.5 L18,18.5", "s", 1.0),
        ("M5.5,6 L15.5,12 L5.5,18 Z", "f", 1.0),
    ],
    "ic_action_play_pause": [
        ("M3,5.5 L12,12 L3,18.5 Z", "f", 1.0),
        ("M15.5,6 L15.5,18 M20.5,6 L20.5,18", "s", 1.0),
    ],
    "ic_action_favorite_previous": [
        ("M14,3.8 L15.7,7.6 L19.8,8 L16.7,10.8 L17.6,14.8 L14,12.7 L10.4,14.8 L11.3,10.8 L8.2,8 L12.3,7.6 Z", "s", 1.0),
        ("M8,20 L3.5,20 M6,17.5 L3.5,20 L6,22.5", "s", 1.0),
    ],
    "ic_action_favorite_next": [
        ("M10,3.8 L11.7,7.6 L15.8,8 L12.7,10.8 L13.6,14.8 L10,12.7 L6.4,14.8 L7.3,10.8 L4.2,8 L8.3,7.6 Z", "s", 1.0),
        ("M16,20 L20.5,20 M18,17.5 L20.5,20 L18,22.5", "s", 1.0),
    ],
    "ic_action_update_install": [
        ("M12,3.5 L12,14 M7.5,9.5 L12,14 L16.5,9.5", "s", 1.0),
        ("M4,15.5 L4,19 Q4,20 5,20 L19,20 Q20,20 20,19 L20,15.5", "s", 1.0),
    ],
    "ic_action_update_cancel": [
        ("M12,12 m-8,0 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0", "s", 1.0),
        ("M12,7.5 L12,12 L15,13.8", "m", 1.0),
        ("M6.3,6.3 L17.7,17.7", "s", 1.0),
    ],
    # Plug with a bolt (start) or a stop square (stop).
    "ic_action_charge_start": [
        ("M8,3 L8,7 M16,3 L16,7", "s", 1.0),
        ("M5.5,7 L18.5,7 L18.5,10 A6.5,6.5 0 0,1 5.5,10 Z", "s", 1.0),
        ("M12,16.5 L12,21", "s", 1.0),
        ("M12.9,8.6 L11,11.4 L13,11.4 L11.4,14", "s", 1.0),
    ],
    "ic_action_charge_stop": [
        ("M8,3 L8,7 M16,3 L16,7", "m", 1.0),
        ("M5.5,7 L18.5,7 L18.5,10 A6.5,6.5 0 0,1 5.5,10 Z", "m", 1.0),
        ("M12,16.5 L12,21", "m", 1.0),
        ("M14.5,13 L21.5,13 L21.5,20 L14.5,20 Z", "f", 1.0),
    ],
    # Battery charged to the everyday limit or full.
    "ic_action_charge_standard": [
        ("M4,7 L18,7 Q19,7 19,8 L19,16 Q19,17 18,17 L4,17 Q3,17 3,16 L3,8 Q3,7 4,7 Z M21,10 L21,14", "s", 1.0),
        ("M5.5,9.5 L13.5,9.5 L13.5,14.5 L5.5,14.5 Z", "f", 1.0),
    ],
    "ic_action_charge_max": [
        ("M4,7 L18,7 Q19,7 19,8 L19,16 Q19,17 18,17 L4,17 Q3,17 3,16 L3,8 Q3,7 4,7 Z M21,10 L21,14", "s", 1.0),
        ("M5.5,9.5 L16.5,9.5 L16.5,14.5 L5.5,14.5 Z", "f", 1.0),
    ],
    # Leaf for low power mode.
    "ic_action_leaf": [
        ("M5,19 Q4,8 19,5 Q20,18 8,17.5", "s", 1.0),
        ("M5,19 Q9,13 14.5,9.5", "s", 1.0),
    ],
    "ic_action_leaf_off": [
        ("M5,19 Q4,8 19,5 Q20,18 8,17.5", "m", 1.0),
        ("M5,19 Q9,13 14.5,9.5", "m", 1.0),
    ] + slash(),
    # The USB symbol, for accessory power (USB and 12 V outlets).
    "ic_action_outlet": [
        ("M12,19 L12,3.5 M9.8,5.7 L12,3.5 L14.2,5.7", "s", 1.0),
        ("M12,14.5 L7.5,12 L7.5,9.6 M12,16.5 L16.5,14 L16.5,11.6", "s", 1.0),
        ("M12,19.6 m-1.9,0 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 M7.5,8.4 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0", "f", 1.0),
        ("M15.2,9 L17.8,9 L17.8,11.6 L15.2,11.6 Z", "f", 1.0),
    ],
    "ic_action_outlet_off": [
        ("M12,19 L12,3.5 M9.8,5.7 L12,3.5 L14.2,5.7", "m", 1.0),
        ("M12,14.5 L7.5,12 L7.5,9.6 M12,16.5 L16.5,14 L16.5,11.6", "m", 1.0),
        ("M12,19.6 m-1.9,0 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 M7.5,8.4 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0", "mf", 1.0),
        ("M15.2,9 L17.8,9 L17.8,11.6 L15.2,11.6 Z", "mf", 1.0),
    ] + slash(),
    # Guest: a person with a name tag.
    "ic_action_guest": [
        ("M10,8 m-3.5,0 a3.5,3.5 0 1,0 7,0 a3.5,3.5 0 1,0 -7,0", "s", 1.0),
        ("M3.5,20 Q3.5,14 10,14 Q12,14 13.4,14.6", "s", 1.0),
        ("M15,15.5 L21,15.5 L21,20.5 L15,20.5 Z M17,18 L19,18", "s", 1.0),
    ],
    "ic_action_guest_off": [
        ("M10,8 m-3.5,0 a3.5,3.5 0 1,0 7,0 a3.5,3.5 0 1,0 -7,0", "m", 1.0),
        ("M3.5,20 Q3.5,14 10,14 Q12,14 13.4,14.6", "m", 1.0),
        ("M15,15.5 L21,15.5 L21,20.5 L15,20.5 Z M17,18 L19,18", "m", 1.0),
    ] + slash(),
    # Seat heating and cooling, for the command list (the Climate screen draws its own seat plan).
    "ic_action_seat_heat_1": seat(1, HEAT_WAVES),
    "ic_action_seat_heat_2": seat(2, HEAT_WAVES),
    "ic_action_seat_cool_2": seat(2, AIR_LINES),
    # Climate keeper modes.
    "ic_action_keeper_on": [
        ("M10,4.5 Q10,3 11.5,3 Q13,3 13,4.5 L13,13.2 A3.6,3.6 0 1,1 10,13.2 Z", "s", 1.0),
        ("M11.5,16.2 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0", "f", 1.0),
        ("M11.5,15 L11.5,8.5", "s", 1.0),
        ("M16,6 L19,6 M16,9 L19,9 M16,12 L18,12", "m", 1.0),
    ],
    "ic_action_keeper_dog": [
        ("M12,15.8 m-3.6,0 a3.6,3.2 0 1,0 7.2,0 a3.6,3.2 0 1,0 -7.2,0", "f", 1.0),
        ("M6.2,10.6 m-1.8,0 a1.8,2.2 0 1,0 3.6,0 a1.8,2.2 0 1,0 -3.6,0 M17.8,10.6 m-1.8,0 a1.8,2.2 0 1,0 3.6,0 a1.8,2.2 0 1,0 -3.6,0", "f", 1.0),
        ("M9.6,6.2 m-1.8,0 a1.8,2.3 0 1,0 3.6,0 a1.8,2.3 0 1,0 -3.6,0 M14.4,6.2 m-1.8,0 a1.8,2.3 0 1,0 3.6,0 a1.8,2.3 0 1,0 -3.6,0", "f", 1.0),
    ],
    "ic_action_keeper_camp": [
        ("M2.5,19.5 L21.5,19.5", "s", 1.0),
        ("M4,19.5 L12,5.5 L20,19.5", "s", 1.0),
        ("M12,5.5 L10.5,3 M12,5.5 L13.5,3", "s", 1.0),
        ("M12,12.5 L9.5,19.5 M12,12.5 L14.5,19.5", "s", 1.0),
    ],
    "ic_action_off": [
        ("M12,12 m-8.5,0 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0", "m", 1.0),
        ("M6,6 L18,18", "s", 1.0),
    ],
    "ic_action_climate": snowflake(),
    "ic_action_on": [
        ("M8.3,6.8 A7,7 0 1,0 15.7,6.8", "s", 1.0),
        ("M12,3.5 L12,11", "s", 1.0),
    ],
    # Cabin overheat protection: cooling with air conditioning, or the fan only.
    "ic_action_cop_on": snowflake(),
    "ic_action_cop_fan": fan(),
    # Activation temperature: a thermometer filled low, medium or high.
    **{f"ic_action_temp_{name}": thermometer(level) for name, level in (("low", 13.2), ("medium", 10.2), ("high", 6.6))},
    # Theme: follow the phone, dark or light.
    "ic_action_theme_system": [
        ("M8,2.5 L16,2.5 Q17.5,2.5 17.5,4 L17.5,20 Q17.5,21.5 16,21.5 L8,21.5 Q6.5,21.5 6.5,20 L6.5,4 Q6.5,2.5 8,2.5 Z", "s", 1.0),
        ("M12,6 L16,6 L16,18 L12,18 Z", "f", 1.0),
    ],
    "ic_action_theme_dark": [("M14.5,3.5 A8.5,8.5 0 1,0 20.5,15 A7,7 0 0,1 14.5,3.5 Z", "s", 1.0)],
    "ic_action_theme_light": sun(),
    # Units: the phone's region, Celsius or Fahrenheit.
    "ic_action_units_auto": [
        ("M12,12 m-8.5,0 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0", "s", 1.0),
        ("M12,3.5 Q7.5,12 12,20.5 Q16.5,12 12,3.5 Z M3.5,12 L20.5,12", "s", 1.0),
    ],
    "ic_action_units_metric": [
        ("M6.5,6.5 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0", "s", 1.0),
        ("M19.5,8.6 A5.6,5.6 0 1,0 19.5,15.4", "s", 1.0),
    ],
    "ic_action_units_imperial": [
        ("M6.5,6.5 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0", "s", 1.0),
        ("M12,18.5 L12,6 L19,6 M12,12 L17.5,12", "s", 1.0),
    ],
    # Parental controls: limits for a driver (a wheel inside a shield).
    "ic_action_parental": [
        ("M12,2.8 L19.5,5.6 L19.5,11.6 Q19.5,17.8 12,21.2 Q4.5,17.8 4.5,11.6 L4.5,5.6 Z", "s", 1.0),
        ("M12,11.6 m-4.3,0 a4.3,4.3 0 1,0 8.6,0 a4.3,4.3 0 1,0 -8.6,0", "s", 1.0),
        ("M7.7,11.6 L10.8,11.6 M13.2,11.6 L16.3,11.6 M12,12.8 L12,15.9", "s", 1.0),
        ("M12,11.6 m-1.2,0 a1.2,1.2 0 1,0 2.4,0 a1.2,1.2 0 1,0 -2.4,0", "f", 1.0),
    ],
    "ic_action_steering_heat": steering_wheel(15, 6) + [
        ("M8.8,7.2 Q8.1,6 8.8,4.8 Q9.5,3.6 8.8,2.4 M12,7.2 Q11.3,6 12,4.8 Q12.7,3.6 12,2.4 M15.2,7.2 Q14.5,6 15.2,4.8 Q15.9,3.6 15.2,2.4", "S", 1.0),
    ],
    "ic_action_role_driver": steering_wheel(12, 8.5),
    "ic_action_role_owner": [
        ("M10,7.5 m-3.5,0 a3.5,3.5 0 1,0 7,0 a3.5,3.5 0 1,0 -7,0", "s", 1.0),
        ("M3.5,20 Q3.5,13.5 10,13.5 Q11.5,13.5 12.6,13.9", "s", 1.0),
        ("M17,15.5 m-2.2,0 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0", "s", 1.0),
        ("M17,17.7 L17,21.5 M17,19.6 L18.6,19.6 M17,21.2 L18.2,21.2", "s", 1.0),
    ],
    # Defrost: the windshield with warm air rising.
    "ic_action_defrost": [
        ("M3,7.5 Q12,3.5 21,7.5 L18.8,17.5 Q12,15.5 5.2,17.5 Z", "s", 1.0),
        ("M9,14.2 Q8.2,12.6 9,11 Q9.8,9.4 9,7.8 M12,13.8 Q11.2,12.2 12,10.6 Q12.8,9 12,7.4 M15,14.2 Q14.2,12.6 15,11 Q15.8,9.4 15,7.8", "S", 1.0),
    ],
    "ic_action_sentry": [
        ("M2.5,12 Q12,3 21.5,12 Q12,21 2.5,12 Z", "s", 1.0),
        ("M12,12 m-3.2,0 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0", "s", 1.0),
        ("M12,12 m-1.3,0 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0", "F", 1.0),
    ],
    "ic_action_speed_limit": [
        ("M4,17 A8.5,8.5 0 1,1 20,17", "s", 1.0),
        ("M12,15.5 L16.2,9.6", "S", 1.0),
        ("M12,15.5 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0", "f", 1.0),
        ("M7,9.5 L8.2,10.6 M12,7.2 L12,8.8 M17,9.5 L15.8,10.6", "m", 1.0),
    ],
    "ic_action_stop": [
        ("M12,12 m-8.5,0 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0", "s", 1.0),
        ("M9,9 L15,9 L15,15 L9,15 Z", "f", 1.0),
    ],
    "ic_action_copy": [
        ("M9.5,8 L18,8 Q19.5,8 19.5,9.5 L19.5,19 Q19.5,20.5 18,20.5 L9.5,20.5 Q8,20.5 8,19 L8,9.5 Q8,8 9.5,8 Z", "s", 1.0),
        ("M5,16 L4.5,16 Q4.5,16 4.5,15 L4.5,5 Q4.5,3.5 6,3.5 L14.5,3.5 Q15.5,3.5 15.5,4.5", "s", 1.0),
    ],
    "ic_action_share": [
        ("M17.5,6 m-2.5,0 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0 M6.5,12 m-2.5,0 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0 M17.5,18 m-2.5,0 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0", "s", 1.0),
        ("M8.7,10.8 L15.3,7.2 M8.7,13.2 L15.3,16.8", "s", 1.0),
    ],
    # A bin, for erasing data.
    "ic_action_erase": [
        ("M5,7 L19,7 M9.5,7 L9.5,5 L14.5,5 L14.5,7", "s", 1.0),
        ("M6.5,7 L7.5,20 L16.5,20 L17.5,7", "s", 1.0),
        ("M10.3,10.5 L10.6,16.5 M13.7,10.5 L13.4,16.5", "s", 1.0),
    ],
}


BOXES = {name: CAR_BOX for name in (
    "ic_action_frunk_closed", "ic_action_frunk_open", "ic_action_trunk_closed", "ic_action_trunk_open",
    "ic_action_sunroof_closed", "ic_action_sunroof_open", "ic_action_tonneau_closed", "ic_action_tonneau_open",
)}


def vector_xml(name, parts):
    x, y, w, h = BOXES.get(name, (0, 0, 24, 24))
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/icons/generate_icons.py; edit that instead. Magenta marks the accent part. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{24 * w / h:g}dp"',
        '    android:height="24dp"',
        f'    android:viewportWidth="{w:g}"',
        f'    android:viewportHeight="{h:g}">',
    ]
    indent = "    "
    if x or y:
        lines.append(f'    <group android:translateX="{-x:g}" android:translateY="{-y:g}">')
        indent = "        "
    for d, kind, alpha in parts:
        color = ACCENT_COLOR if kind in ("S", "F") else MUTED_COLOR if kind in ("m", "mf") else BASE_COLOR
        if kind in ("s", "S", "m"):
            attrs = [f'android:pathData="{d}"', f'android:strokeColor="{color}"', f'android:strokeWidth="{BOLD if kind == "S" else W}"',
                     'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
            if alpha < 1:
                attrs.append(f'android:strokeAlpha="{alpha:.2f}"')
        else:
            attrs = [f'android:pathData="{d}"', f'android:fillColor="{color}"']
            if alpha < 1:
                attrs.append(f'android:fillAlpha="{alpha:.2f}"')
        lines.append(f"{indent}<path\n{indent}    " + f"\n{indent}    ".join(attrs) + " />")
    if x or y:
        lines.append("    </group>")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def svg_sheet(icons, cols=6, cell=120):
    rows = (len(icons) + cols - 1) // cols
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols*cell}" height="{rows*(cell+24)}">',
           '<rect width="100%" height="100%" fill="#26282B"/>']
    for i, (name, parts) in enumerate(icons.items()):
        x0, y0 = (i % cols) * cell, (i // cols) * (cell + 24)
        bx, by, bw, bh = BOXES.get(name, (0, 0, 24, 24))
        scale = min(100 / bw, 100 / bh)
        out.append(f'<g transform="translate({x0+10},{y0+8}) scale({scale:.3f}) translate({-bx},{-by})">')
        for d, kind, alpha in parts:
            color = "#8AB4FF" if kind in ("S", "F") else "#7D8188" if kind in ("m", "mf") else "#ECEDEE"
            if kind in ("s", "S", "m"):
                out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{alpha}" stroke-width="{BOLD if kind == "S" else W}" stroke-linecap="round" stroke-linejoin="round"/>')
            else:
                out.append(f'<path d="{d}" fill="{color}" fill-opacity="{alpha}"/>')
        out.append("</g>")
        out.append(f'<text x="{x0+6}" y="{y0+cell+14}" font-size="12" fill="#B5B8BD" font-family="sans-serif">{name.replace("ic_action_", "")}</text>')
    out.append("</svg>")
    return "\n".join(out)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(SHEET) or ".", exist_ok=True)
    for name, parts in ICONS.items():
        with open(os.path.join(OUT, name + ".xml"), "w") as f:
            f.write(vector_xml(name, parts))
    svg = os.path.splitext(SHEET)[0] + ".svg"
    with open(svg, "w") as f:
        f.write(svg_sheet(ICONS))
    # ImageMagick renders the preview; the drawables don't need it.
    if subprocess.run(["magick", svg, SHEET]).returncode != 0:
        print("no preview: ImageMagick missing")
    print(f"{len(ICONS)} icons -> {OUT}, preview {SHEET}")

"""Build the shared FarmLink model geometry. No external dependencies or image editing.

Run from any directory with Python 3. Texture quadrants in farm_materials.png:
olive, graphite / copper, galvanized steel. Existing state textures stay dynamic.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / 'src/main/resources/assets/homelink_farm/models/block'
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
TILES = {'olive': (0, 0), 'dark': (8, 0), 'copper': (0, 8), 'silver': (8, 8)}


def projected_uv(a, b, side):
    """Minecraft's box projection, explicit so panels and light masks stay aligned."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        'down': [x1, 16 - z2, x2, 16 - z1],
        'up': [x1, z1, x2, z2],
        'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        'south': [x1, 16 - y2, x2, 16 - y1],
        'west': [z1, 16 - y2, z2, 16 - y1],
        'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[side]


def box(a, b, material, overrides=None):
    faces = {}
    for side in SIDES:
        texture = (overrides or {}).get(side, material)
        uv = projected_uv(a, b, side)
        if texture in TILES:
            u, v = TILES[texture]
            # Four image pixels per UV unit in the 64px atlas: divide the projected
            # UVs by four to keep ONE texture pixel per model pixel on every face.
            # The 8px inset also leaves space for the monitor antenna above y=16.
            faces[side] = {'texture': '#materials', 'uv':
                          [u + 2 + uv[0] / 4, v + 2 + uv[1] / 4,
                           u + 2 + uv[2] / 4, v + 2 + uv[3] / 4]}
        else:
            faces[side] = {'texture': '#' + texture, 'uv': uv}
    return {'from': a, 'to': b, 'faces': faces}


def read(name):
    return json.loads((MODELS / (name + '.json')).read_text(encoding='utf-8'))


def write(name, model):
    (MODELS / (name + '.json')).write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')


def model(name, elements):
    data = read(name)
    data['textures']['materials'] = 'homelink_farm:block/farm_materials'
    data['elements'] = elements
    write(name, data)


# Preserve the exact locations and UVs of emissive overlays used by linked variants.
controller = read('farm_controller_template')
glow = [e for e in controller['elements'] if 'neoforge_data' in e]
elements = [box([1.5, 1, 1], [14.5, 8, 15], 'olive', {'north': 'front'}),
            box([1, 8, 1], [15, 9, 15], 'silver'),
            box([1, 9, 1], [15, 10, 8], 'dark', {'up': 'deck'}),
            box([2, 9, 9], [14, 15.5, 14], 'olive', {'north': 'screen'}),
            box([2, 15.5, 8.5], [14, 16, 14.5], 'dark')]
for x in (1, 12):
    for z in (1, 12):
        elements.append(box([x, 0, z], [x + 3, 1, z + 3], 'dark'))
for x in (1, 14.5):
    elements.append(box([x, 2, 2], [x + .5, 7, 14], 'dark'))
for y in (3, 5, 7):
    elements.append(box([4, y, 15], [12, y + .5, 15.25], 'dark'))
model('farm_controller_template', elements + glow)

monitor = read('crop_monitor_template')
glow = [e for e in monitor['elements'] if 'neoforge_data' in e]
elements = [box([3, 0, 3], [13, 1, 13], 'dark'),
            box([6, 1, 6], [10, 2, 10], 'olive'),
            box([7, 2, 7], [9, 7, 9], 'silver'),
            box([2, 7, 4], [14, 14.5, 12], 'olive', {'north': 'front'}),
            box([2, 14.5, 3.5], [14, 15, 12.5], 'dark'),
            box([3, 15, 5], [9, 15.5, 11], 'silver', {'up': 'solar'}),
            box([11, 15, 7.5], [12, 19, 8.5], 'dark'),
            box([2, 7, 3.75], [3, 14.5, 4], 'silver'),
            box([13, 7, 3.75], [14, 14.5, 4], 'silver')]
for x in (3, 11):
    elements.append(box([x, 1, 4], [x + 1, 1.25, 5], 'silver'))
model('crop_monitor_template', elements + glow)

pump = read('irrigation_pump_template')
glow = [e for e in pump['elements'] if 'neoforge_data' in e]
elements = [box([1, 0, 1], [15, 1, 15], 'dark'),
            box([1, 1, 1], [15, 2, 15], 'silver'),
            box([3, 2, 3], [13, 11.5, 13], 'olive'),
            box([3, 11.5, 4.5], [13, 12, 11.5], 'olive'),
            box([4, 3, 1], [12, 11, 3], 'dark', {'north': 'front'}),
            box([4.5, 12, 4.5], [11.5, 13, 11.5], 'silver'),
            box([5, 13, 5], [11, 16, 11], 'copper', {'up': 'pipe_end'})]
# Raised cooling fins and copper retaining bands make the motor legible in profile.
for x in (2.5, 13):
    for z in (4, 6, 8, 10, 12):
        elements.append(box([x, 4, z], [x + .5, 10, z + .5], 'dark'))
for z in (3, 11.5):
    elements.append(box([3, 11.5, z], [13, 12, z + 1.5], 'copper'))
for x in (2, 13):
    for z in (2, 13):
        elements.append(box([x, 2, z], [x + 1, 2.4, z + 1], 'dark'))
model('irrigation_pump_template', elements + glow)

elements = [box([4, 0, 4], [12, 1, 12], 'dark'),
            box([4, 1, 4], [12, 2, 12], 'copper'),
            box([6.5, 2, 6.5], [9.5, 10, 9.5], 'copper'),
            box([6, 3, 6], [10, 4, 10], 'silver'),
            box([6, 8, 6], [10, 9, 10], 'dark'),
            box([5, 10, 5], [11, 12, 11], 'head'),
            box([7, 12, 7], [9, 13, 9], 'silver')]
for a, b in [([2, 10.5, 7.25], [5, 11.5, 8.75]),
             ([11, 10.5, 7.25], [14, 11.5, 8.75]),
             ([7.25, 10.5, 2], [8.75, 11.5, 5]),
             ([7.25, 10.5, 11], [8.75, 11.5, 14])]:
    elements.append(box(a, b, 'copper'))
for a, b in [([1, 10, 7], [2, 12, 9]), ([14, 10, 7], [15, 12, 9]),
             ([7, 10, 1], [9, 12, 2]), ([7, 10, 14], [9, 12, 15])]:
    elements.append(box(a, b, 'dark', {'up': 'silver'}))
model('template_sprinkler', elements)

# Pipes retain their per-variant copper textures, including every oxidation stage.
# A continuous four-pixel tube, including the central junction. Only the half-pixel
# sleeve at a block boundary is wider (4.5px); no oversized cubes at each node.
core = box([6, 6, 6], [10, 10, 10], 'pipe')
for face in core['faces'].values():
    face['uv'] = [6, 6, 10, 10]
data = read('template_pipe_core')
data['elements'] = [core]
write('template_pipe_core', data)


def tube(a, b):
    part = box(a, b, 'pipe')
    for face in part['faces'].values():
        # Sample the clean metal inside the old texture's painted border.
        # The old broad dark bands made short junctions look like wooden crates.
        face['uv'] = [6, 6, 10, 10]
    return part


for name, axis, low, high, outer in [('side', 2, 0, 6, 'north'),
                                    ('up', 1, 10, 16, 'up'),
                                    ('down', 1, 0, 6, 'down')]:
    a, b = [6, 6, 6], [10, 10, 10]
    a[axis], b[axis] = low, high
    shaft = tube(a, b)
    # The collar covers the endpoint; don't draw a coincident shaft cap.
    shaft['faces'].pop(outer)
    c, d = [5.75, 5.75, 5.75], [10.25, 10.25, 10.25]
    c[axis], d[axis] = (low, low + .5) if low == 0 else (high - .5, high)
    collar = tube(c, d)
    data = read('template_pipe_' + name)
    data['elements'] = [shaft, collar]
    write('template_pipe_' + name, data)

data = read('template_pipe_item')
data['elements'] = [tube([6, 6, .5], [10, 10, 15.5]),
                    tube([5.75, 5.75, 0], [10.25, 10.25, .5]),
                    tube([5.75, 5.75, 15.5], [10.25, 10.25, 16])]
for element, side in [(data['elements'][1], 'north'), (data['elements'][2], 'south')]:
    element['faces'][side] = {'texture': '#end', 'uv': [5, 5, 11, 11]}
write('template_pipe_item', data)

print('Updated 9 shared models; all linked, hydraulic and oxidized variants inherit them.')

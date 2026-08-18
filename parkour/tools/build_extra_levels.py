import json
import os
import build_assets as b

levels = b.levels

# Route-changing breakable floor: soft landings are safe, hard impacts destroy it.
b.export([
    b.box((3.2, .22, 3.2), (0, .11, 0), 'metal'),
    b.box((3.0, .05, .16), (0, .245, -1.1), 'beam'),
    b.box((3.0, .05, .16), (0, .245, 1.1), 'beam'),
    b.box((.16, .05, 3.0), (-1.1, .245, 0), 'beam'),
    b.box((.16, .05, 3.0), (1.1, .245, 0), 'beam'),
], os.path.join(b.PR, 'fragile_roof.glb'))

levels.append(b.make(
    9,
    'Водонапорные крыши',
    'Баки, технические мостики и прыжки между маленькими площадками',
    [0, 1.2, 0],
    [61, 7.2, 0],
    [
        ((0, 0, 0), (11, 1, 11), 'roof2'),
        ((14, 2, 2), (7, 1, 7), 'dark'),
        ((29, 4, -2), (7, 1, 7), 'dark'),
        ((45, 5, 3), (8, 1, 8), 'roof2'),
        ((58, 6, 0), (10, 1, 10), 'dark'),
    ],
    [
        ('box', (8, 1.8, -3), (4.5, .24, .5), 'metal'),
        ('pole', (20, 3.0, 0), .22, 5), ('cap', (20, 5.58, 0), (.85, .18, .85)),
        ('pole', (24, 3.3, 3), .22, 5.6), ('cap', (24, 6.18, 3), (.8, .18, .8)),
        ('box', (36, 5.2, 1), (7, .24, .46), 'beam'),
        ('box', (51, 6.4, -2), (6, .24, .46), 'beam'),
    ],
    [{'asset': 'assets/props/crate.glb', 'p': [32, 5.1, -1], 'threshold': 5.8, 'reward': 15}],
    checkpoints=[[29, 4.6, -2], [45, 5.6, 3]],
    theme='highrise'
))

levels.append(b.make(
    10,
    'Промзона',
    'Трубы, низкий техпролёт, хрупкая крыша, стекло и силовые проломы',
    [0, 1.2, 0],
    [66, 4.2, 1],
    [
        ((0, 0, 0), (12, 1, 11), 'concrete'),
        ((18, -1, -3), (8, 1, 8), 'concrete'),
        ((34, 1, 2), (9, 1, 8), 'dark'),
        ((50, 0, -2), (8, 1, 8), 'concrete'),
        ((63, 3, 1), (10, 1, 10), 'dark'),
    ],
    [
        # Bottom is low enough that a standing capsule cannot pass; slide can.
        ('box', (7, 1.88, 0), (4.2, .24, 5.0), 'metal'),
        ('box', (10, 2.0, 3), (8, .34, .55), 'metal'),
        ('box', (25, 1.3, 1), (9, .28, .46), 'beam'),
        ('box', (41, 3.0, -2), (8, .28, .46), 'beam'),
        ('box', (56, 3.2, 2), (7, .28, .46), 'metal'),
    ],
    [
        {'asset': 'assets/props/breakable_barrier.glb', 'p': [29, 1.75, 2], 'r': [0, b.math.pi / 2, 0], 'threshold': 6.8, 'reward': 25},
        {'asset': 'assets/props/fragile_roof.glb', 'p': [43, 2.65, 1], 'threshold': 9.4, 'reward': 30},
        {'asset': 'assets/props/glass_panel.glb', 'p': [55, 3.8, 0], 'r': [0, b.math.pi / 2, 0], 'threshold': 7.0, 'reward': 35},
    ],
    checkpoints=[[34, 1.6, 2], [51, .6, -2]],
    theme='construction'
))

poles = []
for x, z, h in [(10, 0, 4), (15, -3, 5), (21, 2, 5.8), (28, -1, 6.4), (36, 3, 6.8), (44, 0, 7.2)]:
    poles += [('pole', (x, h / 2 - .5, z), .19, h), ('cap', (x, h - .38, z), (.72, .16, .72))]
poles += [
    ('box', (51, 6.9, -2), (8, .22, .4), 'beam'),
    ('box', (62, 7.2, 1), (8, .22, .4), 'beam'),
]
levels.append(b.make(
    11,
    'Лес антенн',
    'Высокие опоры, крошечные вершины и движущаяся секция между ними',
    [0, 1.2, 0],
    [72, 7.4, 0],
    [((0, 0, 0), (10, 1, 10), 'dark'), ((69, 6.2, 0), (10, 1, 10), 'dark')],
    poles,
    movers=[{'asset': 'assets/props/moving_beam.glb', 'p': [55, 7.2, -4], 'axis': 'z', 'distance': 5.5, 'speed': 1.15, 'collider': [4.5, .26, .42]}],
    checkpoints=[[28, 6.2, -1], [51, 7.2, -2]],
    theme='precision'
))

final_extras = [
    ('box', (9, 2.2, 0), (10, .24, .45), 'beam'),
    ('box', (20, 4.0, -3), (9, .24, .45), 'beam'),
    ('pole', (28, 3.0, 1), .2, 6), ('cap', (28, 6.08, 1), (.7, .16, .7)),
    ('pole', (34, 4.0, -2), .2, 8), ('cap', (34, 8.08, -2), (.68, .16, .68)),
    ('box', (42, 8.0, 1), (8, .22, .42), 'beam'),
    ('box', (54, 9.2, -2), (8, .22, .42), 'beam'),
    ('pole', (63, 5.0, 1), .18, 10), ('cap', (63, 10.08, 1), (.64, .15, .64)),
    ('box', (72, 10.0, 0), (9, .22, .4), 'beam'),
]
levels.append(b.make(
    12,
    'Шпиль',
    'Финальная акробатическая линия: высота, край, wall-run, хрупкая секция и сверхточные посадки',
    [0, 1.2, 0],
    [84, 11.2, 0],
    [
        ((0, 0, 0), (10, 1, 10), 'roof'),
        ((81, 10, 0), (10, 1, 10), 'dark'),
    ],
    final_extras,
    [
        {'asset': 'assets/props/fragile_roof.glb', 'p': [48, 8.62, -1], 'threshold': 9.6, 'reward': 45},
        {'asset': 'assets/props/glass_panel.glb', 'p': [77, 10.9, 0], 'r': [0, b.math.pi / 2, 0], 'threshold': 7.2, 'reward': 70},
        {'asset': 'assets/props/breakable_barrier.glb', 'p': [47, 8.7, 1], 'r': [0, b.math.pi / 2, 0], 'threshold': 7.0, 'reward': 35},
    ],
    movers=[{'asset': 'assets/props/moving_beam.glb', 'p': [58, 9.6, 4], 'axis': 'z', 'distance': 5, 'speed': 1.25, 'collider': [4.5, .26, .42]}],
    checkpoints=[[34, 8.3, -2], [63, 10.3, 1]],
    theme='final'
))

with open(os.path.join(b.GEN, 'levels.json'), 'w', encoding='utf-8') as f:
    json.dump(levels, f, ensure_ascii=False, indent=2)
print(f'Baked {len(levels)} static GLB levels into public/assets3d')

#!/usr/bin/env python3
# Draws the case view's static art (needs Pillow): the motherboard, the bus order panel, and the
# small icons. Text (labels, the etched bus order numbers) is drawn by the game with its own
# font, so none is baked in here. The PNGs are committed; rerun after editing this.
#   scripts/art/case_gui_textures.py common/src/main/resources/assets/modularcomputers/textures/gui
#
# Slot positions below must match CaseMenu / CaseScreen; CaseArtTest checks that each slot in
# the code sits on a socket cavity in case_board.png.
import random, sys
from PIL import Image

OUT = sys.argv[1]
rnd = random.Random(7)

GREEN = [(40, 104, 58), (42, 108, 60), (44, 112, 62), (41, 106, 59)]
TRACE = (66, 146, 84)
TRACE_DARK = (34, 88, 49)
GOLD = (204, 166, 74)
COPPER = (214, 150, 74)
BLACK = (26, 26, 30)
SOCKET_IN = (20, 34, 24)
SOCKET_RIM_DARK = (12, 20, 14)
SOCKET_RIM_LIGHT = (90, 150, 104)
SILVER = (176, 180, 188)
LATCH = (230, 230, 230)
PIN = (60, 60, 66)
CHOKE = (96, 96, 104)
CHOKE_CORE = (60, 60, 66)
SILK = (234, 234, 222)
METER_OFF = (30, 38, 33)

BOARD_W, BOARD_H = 168, 132

class Pic:
	def __init__(self, w, h, background=None):
		self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
		if background:
			self.fill(0, 0, w, h, background)
	def fill(self, x0, y0, x1, y1, c):
		"""Fills [x0, x1) x [y0, y1)."""
		for y in range(max(0, y0), min(self.im.height, y1)):
			for x in range(max(0, x0), min(self.im.width, x1)):
				self.im.putpixel((x, y), tuple(c) + (255,))
	def px(self, x, y, c):
		self.fill(x, y, x + 1, y + 1, c)

def board(p, x0, y0, x1, y1):
	for y in range(y0, y1):
		for x in range(x0, x1):
			p.px(x, y, rnd.choice(GREEN))
	p.fill(x0, y0, x1, y0 + 1, TRACE_DARK); p.fill(x0, y1 - 1, x1, y1, TRACE_DARK)
	p.fill(x0, y0, x0 + 1, y1, TRACE_DARK); p.fill(x1 - 1, y0, x1, y1, TRACE_DARK)
	for hx, hy in [(x0 + 3, y0 + 3), (x1 - 8, y0 + 3), (x0 + 3, y1 - 8), (x1 - 8, y1 - 8)]:
		hole(p, hx, hy)

def hole(p, x, y):  # a mounting hole: gold ring, dark center
	p.fill(x, y + 1, x + 5, y + 4, GOLD); p.fill(x + 1, y, x + 4, y + 5, GOLD)
	p.fill(x + 1, y + 1, x + 4, y + 4, BLACK)

def via(p, x, y):
	p.fill(x, y, x + 3, y + 3, GOLD); p.px(x + 1, y + 1, BLACK)

def route(p, points, col=TRACE, dotted=False, phase=0):
	"""Runs through points: straight, or 45-degree diagonals. Dotted draws every other pixel."""
	n = phase
	for (ax, ay), (bx, by) in zip(points, points[1:]):
		steps = max(abs(bx - ax), abs(by - ay))
		for i in range(steps + 1):
			x = ax + (0 if bx == ax else (i if bx > ax else -i))
			y = ay + (0 if by == ay else (i if by > ay else -i))
			if not dotted or n % 2 == 0:
				p.px(x, y, col)
			n += 1

def bus(p, points, n=3, spacing=2, horizontal_first=True):
	for k in range(n):
		off = k * spacing
		route(p, [(x + (0 if horizontal_first else off), y + (off if horizontal_first else 0)) for x, y in points])

def gold_dots(p, points, phase=0):
	route(p, points, GOLD, dotted=True, phase=phase)

def corners(p, x0, y0, x1, y1):
	for cx, cy, dx, dy in [(x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)]:
		p.fill(min(cx, cx + 3 * dx), cy, max(cx, cx + 3 * dx) + 1, cy + 1, SILK)
		p.fill(cx, min(cy, cy + 3 * dy), cx + 1, max(cy, cy + 3 * dy) + 1, SILK)

def cavity(p, x, y):
	"""An item slot as a socket cavity; (x, y) is the item's top-left, like a vanilla slot."""
	p.fill(x - 1, y - 1, x + 17, y + 17, SOCKET_RIM_LIGHT)
	p.fill(x - 1, y - 1, x + 16, y + 16, SOCKET_RIM_DARK)
	p.fill(x, y, x + 16, y + 16, SOCKET_IN)

def cpu_socket(p, x, y):
	p.fill(x - 6, y - 6, x + 22, y + 22, BLACK)
	for i in range(-4, 20, 2):
		for qx, qy in [(x + i, y - 4), (x + i, y + 19), (x - 4, y + i), (x + 19, y + i)]:
			p.px(qx, qy, (110, 110, 118))
	p.fill(x + 23, y - 6, x + 25, y + 22, SILVER)  # retention lever
	p.fill(x + 23, y - 8, x + 28, y - 6, SILVER)
	cavity(p, x, y)

def choke(p, x, y):
	p.fill(x, y, x + 5, y + 5, CHOKE); p.fill(x + 1, y + 1, x + 4, y + 4, CHOKE_CORE)

def resistor_array(p, x, y):
	p.fill(x, y, x + 6, y + 2, BLACK)
	for qx in range(x, x + 6, 2):
		p.px(qx, y - 1, SILVER); p.px(qx, y + 2, SILVER)

def crystal(p, x, y):
	p.fill(x + 1, y, x + 7, y + 4, SILVER); p.fill(x, y + 1, x + 8, y + 3, SILVER)
	p.fill(x + 2, y + 1, x + 6, y + 3, (140, 144, 152))

def dip_socket(p, x, y):
	p.fill(x - 4, y - 2, x + 20, y + 18, BLACK)
	for qy in range(y, y + 16, 3):
		p.fill(x - 3, qy, x - 1, qy + 1, SILVER); p.fill(x + 17, qy, x + 19, qy + 1, SILVER)
	p.fill(x + 7, y - 2, x + 9, y - 1, PIN)
	cavity(p, x, y)

def dimm(p, x, y):  # an upright memory connector, latches open
	p.fill(x + 5, y - 22, x + 11, y + 38, BLACK)
	p.fill(x + 4, y - 25, x + 12, y - 22, LATCH); p.fill(x + 4, y + 38, x + 12, y + 41, LATCH)
	for qy in range(y - 20, y + 37, 3):
		p.fill(x + 7, qy, x + 9, qy + 1, PIN)
	cavity(p, x, y)

def sata(p, x, y):
	p.fill(x + 18, y + 3, x + 24, y + 13, BLACK); p.fill(x + 18, y + 3, x + 21, y + 6, PIN)
	cavity(p, x, y)

def exp(p, x, y):
	p.fill(x - 5, y + 5, x + 19, y + 11, BLACK)
	p.fill(x + 17, y + 5, x + 18, y + 11, PIN)
	for qx in range(x - 4, x + 19, 2):
		p.fill(qx, y + 7, qx + 1, y + 9, (54, 54, 60))
	cavity(p, x, y)

def heatsink(p, x, y, w, h):
	p.fill(x, y, x + w, y + h, (150, 156, 166))
	for fx in range(x + 1, x + w, 2):
		p.fill(fx, y, fx + 1, y + h, (110, 116, 126))
	p.fill(x, y, x + w, y + 1, (196, 200, 208))

def fan_header(p, x, y):
	p.fill(x, y, x + 7, y + 4, (236, 236, 236))
	for qx in (x + 1, x + 3, x + 5):
		p.px(qx, y + 1, GOLD)

def charge_meter(p, x, y, segments=6):
	p.fill(x, y, x + segments * 4 + 1, y + 6, BLACK)
	for i in range(segments):
		p.fill(x + 1 + i * 4, y + 1, x + 4 + i * 4, y + 5, METER_OFF)

# Board coordinates (the board texture's own pixels). The game draws the board at panel (4, 4).
CPU = (14, 22)
CHOKES = [(2, 18 + 7 * i) for i in range(4)]
ROM = (16, 64)
UPS = [(4 + 20 * i, 99) for i in range(3)]  # three battery bays
METER = (20, 119)
RAM = [(50 + 20 * i, 36) for i in range(4)]
SATA = [(134, 15 + 20 * i) for i in range(4)]
EXP = [(70 + 24 * i, 110) for i in range(4)]

TIN = (198, 202, 208)  # bare solder pads, tinned

def silk_box(p, x0, y0, x1, y1):
	"""A footprint's silkscreen outline (x1, y1 exclusive), dashed: the printed mark of a part that isn't there."""
	for x in range(x0, x1):
		if (x - x0) % 3 != 2:
			p.px(x, y0, SILK); p.px(x, y1 - 1, SILK)
	for y in range(y0, y1):
		if (y - y0) % 3 != 2:
			p.px(x0, y, SILK); p.px(x1 - 1, y, SILK)

def bare_cavity(p, x, y):
	"""An unpopulated slot's footprint: its outline and two pads."""
	silk_box(p, x - 1, y - 1, x + 17, y + 17)
	p.fill(x + 3, y + 7, x + 5, y + 9, TIN); p.fill(x + 11, y + 7, x + 13, y + 9, TIN)

def bare_dimm(p, x, y):
	"""An unpopulated memory connector: its outline and a column of through-hole pads."""
	silk_box(p, x + 4, y - 25, x + 12, y + 41)
	for qy in range(y - 20, y + 37, 3):
		p.fill(x + 7, qy, x + 9, qy + 1, TIN)

def bare_sata(p, x, y):
	"""An unpopulated SATA port: the drive's outline and the port's pads."""
	silk_box(p, x - 1, y - 1, x + 17, y + 17)
	silk_box(p, x + 18, y + 3, x + 24, y + 13)
	for qy in range(y + 5, y + 12, 2):
		p.px(x + 20, qy, TIN); p.px(x + 22, qy, TIN)

def bare_exp(p, x, y):
	"""An unpopulated expansion connector: the card's and the connector's outlines, a row of pads."""
	silk_box(p, x - 1, y - 1, x + 17, y + 17)
	silk_box(p, x - 5, y + 5, x + 19, y + 11)
	for qx in range(x - 3, x + 18, 2):
		p.px(qx, y + 7, TIN); p.px(qx, y + 8, TIN)

def case_board(bare=False):
	"""The full board, every part fitted; or, with bare, the same board with every part's
	footprint left unpopulated, as a fab leaves a cheaper board's missing parts out. The case
	view shows the bare board's pixels where a lower tier has no slot."""
	p = Pic(BOARD_W, BOARD_H)
	board(p, 0, 0, BOARD_W, BOARD_H)
	# copper buses and gold dotted runs, under the parts
	bus(p, [(37, 26), (42, 26), (46, 28), (50, 28)], n=4)
	for k in range(4):
		route(p, [(46, 28 + 2 * k), (128, 28 + 2 * k)])
	bus(p, [(18, 47), (18, 60)], n=3, horizontal_first=False)
	bus(p, [(30, 47), (30, 54), (40, 64), (40, 92)], n=3, horizontal_first=False)
	for k in range(3):
		route(p, [(40, 92 + 2 * k), (150, 92 + 2 * k)])
	for k in range(2):
		route(p, [(131 + 2 * k, 34), (131 + 2 * k, 92)])
	gold_dots(p, [(44, 50), (44, 84), (126, 84)])
	gold_dots(p, [(36, 42), (46, 42), (46, 82)], phase=1)
	gold_dots(p, [(10, 70), (6, 66), (6, 50)])
	gold_dots(p, [(126, 18), (130, 22), (130, 30)])
	gold_dots(p, [(66, 9), (72, 13), (72, 14)], phase=1)
	for v in [(42, 25), (17, 59), (39, 89), (130, 91), (44, 49), (126, 83)]:
		via(p, *v)
	for tx, ty in [(26, 56), (100, 88), (150, 98)]:
		p.fill(tx, ty, tx + 2, ty + 2, COPPER)
	# CPU and its power stage
	for c in CHOKES:
		choke(p, *c)
	cpu_socket(p, *CPU)
	resistor_array(p, CPU[0] + 19, CPU[1] + 26)
	p.fill(CPU[0] + 27, CPU[1] - 8, CPU[0] + 29, CPU[1] - 7, (90, 230, 110))  # power LED
	corners(p, CPU[0] - 8, CPU[1] - 8, CPU[0] + 28, CPU[1] + 24)
	crystal(p, 36, 50)
	# firmware, UPS battery
	dip_socket(p, *ROM)
	corners(p, 10, 60, 38, 84)
	for u in UPS:
		(bare_cavity if bare else cavity)(p, *u)
	charge_meter(p, *METER)
	# memory, chipset, drives, expansion
	for r in RAM:
		(bare_dimm if bare else dimm)(p, *r)
	corners(p, 52, 9, 124, 79)
	heatsink(p, 46, 3, 18, 7)
	resistor_array(p, 104, 4); resistor_array(p, 112, 4)
	fan_header(p, 98, 82)
	p.fill(110, 82, 112, 83, (240, 180, 60)); p.fill(114, 82, 116, 83, (90, 230, 110))
	for s in SATA:
		(bare_sata if bare else sata)(p, *s)
	for e in EXP:
		(bare_exp if bare else exp)(p, *e)
	return p.im

# The bot golem's board (user design, 2026-10-24): the same board, seen through its iron chest
# plate (riveted, vines grown over it, as an iron golem's), with its robotics device soldered onto
# the last expansion lane: a big chip, no connector.
IRON = [(201, 194, 181), (197, 190, 177), (205, 198, 185)]  # an iron golem's warm grey, brushed
IRON_SHADE = (138, 131, 119)
IRON_LIGHT = (228, 222, 210)
RIVET = (112, 106, 96)
VINE = (58, 110, 40)
VINE_DARK = (40, 82, 28)
LEAF = (88, 148, 56)
ROBOTICS_LANE = 3

PANEL_W, PANEL_H = 176, 230  # the whole view, as CaseScreen's
BOARD_AT = (4, 4)  # where the board shows through it (CaseMenu.BOARD_X, BOARD_Y)

def strand(p, points, vr, thick=2):
	"""A vine along the points, {thick} wide, leaf clusters here and there."""
	for (x0, y0), (x1, y1) in zip(points, points[1:]):
		steps = max(abs(x1 - x0), abs(y1 - y0))
		for i in range(steps + 1):
			x = x0 + (x1 - x0) * i // max(steps, 1)
			y = y0 + (y1 - y0) * i // max(steps, 1)
			for t in range(thick):
				p.px(x + t, y, VINE if vr.random() < 0.65 else VINE_DARK)
			if vr.random() < 0.12:
				lx = x + vr.choice((-2, thick + 1))
				for dx, dy in [(0, 0), (1, 0), (0, -1), (1, -1), (0, 1)]:
					p.px(lx + dx, y + dy, LEAF if vr.random() < 0.7 else VINE)

def golem_panel():
	"""The golem's chest, open: iron plates (seams, rivets, weathering) with vines grown over them,
	and the opening the board shows through, its lip in shadow."""
	pr = random.Random(23)
	p = Pic(PANEL_W, PANEL_H)
	tone = {}  # each plate a little lighter or darker than the next
	for y in range(PANEL_H):
		row = pr.choice(IRON)
		for x in range(PANEL_W):
			c = row if pr.random() < 0.8 else pr.choice(IRON)  # brushed: rows of one shade
			plate = (y // 46, (x + (30 if (y // 46) % 2 else 60)) // 88)
			d = tone.setdefault(plate, pr.choice((-6, -3, 0, 3)))
			c = tuple(max(0, min(255, v + d)) for v in c)
			if pr.random() < 0.006:
				c = IRON_SHADE  # a pit
			p.px(x, y, c)
	# plates: seams across, and between plates down, offset row by row, riveted at the seams
	rows = [0, 46, 92, 140, 186, PANEL_H]
	for i, top in enumerate(rows[:-1]):
		bottom = rows[i + 1]
		p.fill(0, top, PANEL_W, top + 1, IRON_SHADE)
		p.fill(0, top + 1, PANEL_W, top + 2, IRON_LIGHT)
		start = 30 if i % 2 else 60
		for sx in range(start, PANEL_W, 88):
			p.fill(sx, top, sx + 1, bottom, IRON_SHADE)
			p.fill(sx + 1, top, sx + 2, bottom, IRON_LIGHT)
		for rx in range(6, PANEL_W - 4, 14):
			p.fill(rx, top + 4, rx + 2, top + 6, RIVET); p.px(rx, top + 4, IRON_LIGHT)
	# the outer bevel
	p.fill(0, 0, PANEL_W, 1, IRON_LIGHT); p.fill(0, 0, 1, PANEL_H, IRON_LIGHT)
	p.fill(0, PANEL_H - 1, PANEL_W, PANEL_H, IRON_SHADE); p.fill(PANEL_W - 1, 0, PANEL_W, PANEL_H, IRON_SHADE)
	# the opening's lip: dark above and left (in shadow), light below and right
	bx, by = BOARD_AT
	p.fill(bx - 2, by - 2, bx + BOARD_W + 2, by + BOARD_H + 2, IRON_SHADE)
	p.fill(bx - 1, by + BOARD_H, bx + BOARD_W + 2, by + BOARD_H + 2, IRON_LIGHT)
	p.fill(bx + BOARD_W, by - 1, bx + BOARD_W + 2, by + BOARD_H + 2, IRON_LIGHT)
	p.fill(bx, by, bx + BOARD_W, by + BOARD_H, (0, 0, 0))
	# vines: up the left side from the bottom, along the top, over the lower plates
	vr = random.Random(11)
	strand(p, [(2, PANEL_H - 1), (1, 200), (3, 170), (1, 140), (2, 110), (1, 70), (2, 40), (1, 2)], vr)
	strand(p, [(1, 1), (30, 2), (58, 1), (90, 2), (118, 1)], vr)
	strand(p, [(1, 150), (12, 148), (20, 146)], vr)
	strand(p, [(PANEL_W - 2, PANEL_H - 1), (PANEL_W - 3, 214), (PANEL_W - 1, 196), (PANEL_W - 12, 190)], vr)
	return p.im

def tendrils(p):
	"""Vine tendrils reaching onto the board from the plate, where no part is."""
	vr = random.Random(13)
	strand(p, [(88, 0), (89, 4), (87, 7)], vr, thick=1)
	strand(p, [(0, 88), (3, 90), (5, 93)], vr, thick=1)
	strand(p, [(BOARD_W - 1, 100), (BOARD_W - 3, 103)], vr, thick=1)

def robotics_chip(p, x, y):
	"""The robotics device, soldered where the lane's connector would be: a large chip, pins all round."""
	p.fill(x - 5, y - 1, x + 19, y + 17, (34, 88, 49))  # its pad, the board showing
	p.fill(x - 3, y + 1, x + 17, y + 15, BLACK)
	for qx in range(x - 2, x + 17, 2):
		p.px(qx, y, GOLD); p.px(qx, y + 15, GOLD)
	for qy in range(y + 2, y + 15, 2):
		p.px(x - 4, qy, GOLD); p.px(x + 17, qy, GOLD)
	p.fill(x - 1, y + 3, x + 15, y + 4, (60, 60, 66))  # its die's edge
	# a little gear: what it drives
	cx, cy = x + 7, y + 8
	p.fill(cx - 2, cy - 2, cx + 3, cy + 3, SILVER)
	for dx, dy in [(0, -3), (0, 3), (-3, 0), (3, 0)]:
		p.px(cx + dx, cy + dy, SILVER)
	p.px(cx, cy, BLACK)
	p.px(x - 2, y + 13, SILK)  # pin 1

def golem_board(bare=False):
	p = Pic(BOARD_W, BOARD_H)
	p.im = case_board(bare)
	tendrils(p)
	robotics_chip(p, *EXP[ROBOTICS_LANE])
	return p.im

BUS_W, BUS_H = 176, 230

def bus_panel():
	"""The bus order panel's backing: just the board. Rows are drawn per device (bus_row.png)."""
	p = Pic(BUS_W, BUS_H)
	p.fill(0, 0, BUS_W, BUS_H, (0, 0, 0))
	board(p, 1, 1, BUS_W - 1, BUS_H - 1)
	gold_dots(p, [(BUS_W - 4, 30), (BUS_W - 4, BUS_H - 12)])
	return p.im

ROW_W, ROW_H = 141, 18  # drawn at panel x 19; the display ends 14 short of the panel's edge

def bus_row():
	"""One bus order row: the device's icon socket, a gap, then its dark display with header pins."""
	p = Pic(ROW_W, ROW_H)
	cavity(p, 1, 1)
	p.fill(19, 0, ROW_W, ROW_H, BLACK)
	for x in range(21, ROW_W - 2, 3):
		p.px(x, 0, GOLD)
	return p.im

def ghost(draw):
	p = Pic(16, 16)
	g, d = (70, 104, 80), (52, 80, 60)
	draw(p, g, d)
	return p.im

def cpu_ghost(p, g, d):
	p.fill(3, 3, 13, 13, g)
	for i in range(4, 12, 2):
		for x, y in [(i, 1), (i, 2), (i, 13), (i, 14), (1, i), (2, i), (13, i), (14, i)]:
			p.px(x, y, g)
	p.fill(5, 5, 11, 11, d)
def rom_ghost(p, g, d):
	p.fill(2, 5, 14, 11, g)
	for x in range(4, 12, 2):
		p.px(x, 4, g); p.px(x, 11, g)
	p.fill(4, 7, 12, 9, d)
def ram_ghost(p, g, d):
	p.fill(1, 5, 15, 11, g)
	for x in (2, 5, 8, 11):
		p.fill(x, 6, x + 2, 9, d)
	for x in range(2, 14, 2):
		p.px(x, 11, g)
def disk_ghost(p, g, d):
	p.fill(1, 4, 15, 12, g); p.fill(3, 6, 13, 7, d); p.px(12, 9, d)
def card_ghost(p, g, d):
	p.fill(2, 4, 13, 11, g); p.fill(13, 2, 14, 13, g)
	for x in range(3, 11, 2):
		p.px(x, 11, g)
	p.fill(4, 6, 7, 9, d)
def ups_ghost(p, g, d):
	p.fill(2, 5, 14, 14, g); p.fill(4, 3, 6, 5, g); p.fill(10, 3, 12, 5, g)
	p.fill(6, 8, 10, 10, d); p.fill(7, 7, 9, 11, d)

def bus_icon(kind):
	p = Pic(16, 16)
	if kind == "display":
		p.fill(1, 2, 15, 12, (206, 197, 168)); p.fill(3, 4, 13, 10, (10, 10, 12)); p.fill(5, 12, 11, 14, (172, 163, 135))
	elif kind == "keyboard":
		p.fill(0, 5, 16, 12, (66, 64, 59))
		for x in range(1, 15, 2):
			p.px(x, 7, (196, 191, 176)); p.px(x, 9, (196, 191, 176))
	elif kind == "robotics":  # the golem's soldered chip: pins all round, a gear on it
		p.fill(3, 3, 13, 13, BLACK)
		for q in range(4, 13, 2):
			p.px(q, 2, GOLD); p.px(q, 13, GOLD); p.px(2, q, GOLD); p.px(13, q, GOLD)
		p.fill(6, 6, 10, 10, SILVER)
		for dx, dy in [(7, 5), (8, 5), (7, 10), (8, 10), (5, 7), (5, 8), (10, 7), (10, 8)]:
			p.px(dx, dy, SILVER)
		p.px(7, 7, BLACK); p.px(8, 8, BLACK)
	return p.im

def icons():
	"""One row of 16x16 icons: slot ghosts (cpu, rom, ram, disk, card, ups), then built-in bus devices (display, keyboard, robotics)."""
	sheet = Image.new("RGBA", (16 * 9, 16), (0, 0, 0, 0))
	for i, im in enumerate([ghost(cpu_ghost), ghost(rom_ghost), ghost(ram_ghost), ghost(disk_ghost),
			ghost(card_ghost), ghost(ups_ghost), bus_icon("display"), bus_icon("keyboard"), bus_icon("robotics")]):
		sheet.paste(im, (16 * i, 0))
	return sheet

state = rnd.getstate()
case_board().save(f"{OUT}/case_board.png")
after = rnd.getstate()
rnd.setstate(state)  # the same copper noise and traces
case_board(bare=True).save(f"{OUT}/case_board_bare.png")
rnd.setstate(after)
bus_panel().save(f"{OUT}/bus_panel.png")
bus_row().save(f"{OUT}/bus_row.png")
icons().save(f"{OUT}/case_icons.png")
state = rnd.getstate()
golem_board().save(f"{OUT}/golem_board.png")
after = rnd.getstate()
rnd.setstate(state)
golem_board(bare=True).save(f"{OUT}/golem_board_bare.png")
rnd.setstate(after)
golem_panel().save(f"{OUT}/golem_panel.png")

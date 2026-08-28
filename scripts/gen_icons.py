#!/usr/bin/env python3
"""生成 tabBar 占位图标（81x81 RGBA PNG，无第三方依赖）。上线前可替换为设计稿图标。"""
import struct, zlib, math, os

SIZE = 81
GRAY = (156, 163, 175, 255)   # #9CA3AF
GREEN = (34, 197, 94, 255)    # #22C55E
CLEAR = (0, 0, 0, 0)

def new_canvas():
    return [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]

def fill_circle(buf, cx, cy, r, color):
    for y in range(SIZE):
        for x in range(SIZE):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                buf[y][x] = color

def ring(buf, cx, cy, r, thick, color):
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy)
            if r - thick / 2 <= d <= r + thick / 2:
                buf[y][x] = color

def rounded_rect(buf, x0, y0, x1, y1, rad, color):
    for y in range(SIZE):
        for x in range(SIZE):
            if x0 <= x <= x1 and y0 <= y <= y1:
                # 距离四个圆角
                dx = max(x0 + rad - x, x - (x1 - rad), 0)
                dy = max(y0 + rad - y, y - (y1 - rad), 0)
                if dx * dx + dy * dy <= rad * rad or (dx == 0 or dy == 0) and (dx + dy) <= rad:
                    if dx == 0 and dy == 0:
                        buf[y][x] = color
                    elif dx * dx + dy * dy <= rad * rad:
                        buf[y][x] = color

def triangle(buf, p1, p2, p3, color):
    def sign(a, b, p):
        return (p[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (p[1] - b[1])
    for y in range(SIZE):
        for x in range(SIZE):
            p = (x, y)
            d1 = sign(p1, p2, p); d2 = sign(p2, p3, p); d3 = sign(p3, p1, p)
            neg = d1 < 0 or d2 < 0 or d3 < 0
            pos = d1 > 0 or d2 > 0 or d3 > 0
            if not (neg and pos):
                buf[y][x] = color

def icon_today(color):
    b = new_canvas()
    ring(b, 40, 40, 24, 7, color)
    fill_circle(b, 40, 40, 6, color)
    return b

def icon_speak(color):
    b = new_canvas()
    rounded_rect(b, 12, 14, 68, 54, 12, color)
    triangle(b, (24, 52), (44, 52), (22, 70), color)
    return b

def icon_vocab(color):
    b = new_canvas()
    rounded_rect(b, 10, 20, 38, 62, 6, color)
    rounded_rect(b, 42, 20, 70, 62, 6, color)
    for y in range(SIZE):
        for x in range(38, 42):
            b[y][x] = CLEAR
    return b

def icon_course(color):
    b = new_canvas()
    # 学士帽：菱形帽面 + 帽底 + 流苏
    for y in range(SIZE):
        for x in range(SIZE):
            if abs(x - 40) + abs(y - 24) <= 22:
                b[y][x] = color
    rounded_rect(b, 22, 42, 58, 54, 5, color)
    fill_circle(b, 59, 36, 4, color)
    return b

def icon_mine(color):
    b = new_canvas()
    fill_circle(b, 40, 28, 13, color)
    rounded_rect(b, 18, 46, 62, 74, 16, color)
    return b

def write_png(path, buf):
    raw = b""
    for row in buf:
        raw += b"\x00" + b"".join(struct.pack("4B", *px) for px in row)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "static")
os.makedirs(OUT, exist_ok=True)
icons = {
    "tab-today": icon_today, "tab-course": icon_course, "tab-speak": icon_speak,
    "tab-vocab": icon_vocab, "tab-mine": icon_mine,
}
for name, fn in icons.items():
    write_png(os.path.join(OUT, f"{name}.png"), fn(GRAY))
    write_png(os.path.join(OUT, f"{name}-active.png"), fn(GREEN))
print("icons written to", os.path.abspath(OUT))

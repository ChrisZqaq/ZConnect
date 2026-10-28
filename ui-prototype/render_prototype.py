from PIL import Image, ImageDraw, ImageFont
import os

W, H = 1340, 1030
BG = "#E9E7DF"
INK = "#171814"
MUTED = "#6D6E66"
PHONE = "#111210"
PANEL = "#22231F"
PANEL_2 = "#2B2C27"
WHITE = "#F4F2E9"
GREY = "#A9AAA0"
YELLOW = "#FFCD00"
RED = "#FF8E85"
GREEN = "#B7DC8A"
REGULAR = os.environ.get("DAIMON_CJK_FONT_REGULAR", "/System/Library/Fonts/PingFang.ttc")
BOLD = os.environ.get("DAIMON_CJK_FONT_BOLD", REGULAR)

im = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(im)

def f(size, bold=False):
    return ImageFont.truetype(BOLD if bold else REGULAR, size)

def txt(x, y, s, size=16, color=INK, bold=False, anchor=None, draw=None):
    (draw or d).text((x, y), s, font=f(size, bold), fill=color, anchor=anchor)

def rr(box, radius, fill, outline=None, width=1, draw=None):
    (draw or d).rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

def phone_shell(x, y):
    rr((x, y, x + 400, y + 830), 38, "#070806")
    rr((x + 9, y + 9, x + 391, y + 821), 31, PHONE)
    rr((x + 159, y + 16, x + 241, y + 23), 4, "#3B3C37")
    txt(x + 24, y + 29, "9:41", 12, WHITE, True)
    txt(x + 340, y + 29, "●  Wi-Fi", 10, WHITE, anchor="ra")
    return x + 9, y + 9

def header(x, y, subtitle):
    txt(x + 22, y + 59, "ZF RELAY", 23, WHITE, True)
    txt(x + 22, y + 89, subtitle, 12, GREY)
    d.line((x + 20, y + 116, x + 362, y + 116), fill=PANEL, width=1)

def bottom_nav(x, y, active):
    d.line((x + 12, y + 748, x + 370, y + 748), fill=PANEL, width=1)
    items = [("▦", "照片"), ("↓", "传输")]
    for i, (icon, label) in enumerate(items):
        cx = x + 104 + i * 174
        color = YELLOW if i == active else GREY
        txt(cx, y + 762, icon, 24, color, True, anchor="ma")
        txt(cx, y + 793, label, 12, color, i == active, anchor="ma")

def thumbnail(x, y, w, h, variant):
    colors = [("#78827B", "#C8B27C"), ("#53646B", "#A8B4A2"), ("#75685A", "#C49B79"), ("#4C5550", "#919D89")]
    top, bottom = colors[variant % len(colors)]
    d.rectangle((x, y, x + w, y + h), fill=top)
    d.ellipse((x + w - 45, y + 14, x + w - 25, y + 34), fill=bottom)
    d.polygon([(x, y + h), (x + 35, y + 43), (x + 76, y + 82), (x + 111, y + 35), (x + w, y + 80), (x + w, y + h)], fill=bottom)
    d.polygon([(x, y + h), (x + 53, y + 70), (x + 88, y + h)], fill=PANEL_2)

def connection_screen(x, y):
    header(x, y, "Nikon Zf · Android")
    txt(x + 22, y + 139, "把 Zf 的照片，", 30, WHITE, True)
    txt(x + 22, y + 180, "直接带回手机。", 30, WHITE, True)
    txt(x + 22, y + 225, "原生无线连接 · 只读取，不删除", 13, GREY)
    txt(x + 22, y + 264, "连接方式", 13, WHITE, True)
    rr((x + 22, y + 291, x + 188, y + 333), 10, YELLOW)
    txt(x + 105, y + 312, "智能设备", 14, PHONE, True, anchor="mm")
    rr((x + 194, y + 291, x + 360, y + 333), 10, PANEL, "#45463F")
    txt(x + 277, y + 312, "计算机 · 待验证", 12, GREY, anchor="mm")
    rr((x + 22, y + 351, x + 360, y + 527), 16, PANEL)
    steps = [
        ("1", "相机开启 Wi-Fi", "网络菜单 → 连接至智能设备"),
        ("2", "手机加入相机热点", "无互联网时选择“保持连接”"),
        ("3", "退出 SnapBridge", "同一时刻只保留一个客户端"),
    ]
    for i, (n, title, body) in enumerate(steps):
        sy = y + 370 + i * 51
        rr((x + 38, sy, x + 64, sy + 26), 6, YELLOW)
        txt(x + 51, sy + 13, n, 13, PHONE, True, anchor="mm")
        txt(x + 77, sy - 1, title, 14, WHITE, True)
        txt(x + 77, sy + 23, body, 11, GREY)
    rr((x + 22, y + 546, x + 360, y + 594), 11, PHONE, "#45463F")
    txt(x + 38, y + 570, "手动相机 IP（通常留空）", 12, GREY, anchor="lm")
    rr((x + 22, y + 611, x + 360, y + 665), 13, YELLOW)
    txt(x + 191, y + 638, "连接 Zf", 17, PHONE, True, anchor="mm")
    txt(x + 191, y + 690, "一加：关闭智能网络切换", 12, GREY, anchor="ma")
    txt(x + 191, y + 713, "下载期间保持 App 在前台", 12, GREY, anchor="ma")

def gallery_screen(x, y):
    header(x, y, "Nikon Z f  ·  固件 1.30")
    txt(x + 358, y + 74, "刷新", 12, GREY, anchor="rm")
    txt(x + 20, y + 136, "机内照片", 23, WHITE, True)
    txt(x + 20, y + 169, "共 128 个对象 · 显示最近 4 张", 11, GREY)
    names = [("DSC_0142.JPG", "8.5 MB", "JPEG"), ("DSC_0141.NEF", "28.4 MB", "NEF"), ("DSC_0140.JPG", "7.9 MB", "JPEG"), ("DSC_0139.JPG", "8.1 MB", "JPEG")]
    for i, (name, size, kind) in enumerate(names):
        col, row = i % 2, i // 2
        cx = x + 18 + col * 177
        cy = y + 199 + row * 254
        rr((cx, cy, cx + 166, cy + 239), 14, PANEL)
        thumbnail(cx + 1, cy + 1, 164, 105, i)
        txt(cx + 11, cy + 119, name, 12, WHITE, True)
        txt(cx + 11, cy + 141, f"{size} · {kind}", 10, GREY)
        if kind == "JPEG":
            rr((cx + 10, cy + 173, cx + 156, cy + 213), 9, YELLOW)
            txt(cx + 83, cy + 193, "下载原图", 12, PHONE, True, anchor="mm")
        else:
            rr((cx + 10, cy + 173, cx + 156, cy + 213), 9, PANEL_2)
            txt(cx + 83, cy + 193, "RAW 待验证", 11, GREY, anchor="mm")
    bottom_nav(x, y, 0)

def transfer_screen(x, y):
    header(x, y, "传输队列 · 3 项")
    txt(x + 20, y + 137, "传输", 23, WHITE, True)
    cards = [
        ("DSC_0142.JPG", "已完成", "8.5 / 8.5 MB", 1.0, GREEN, None),
        ("DSC_0140.JPG", "下载中  62%", "5.0 / 8.1 MB", 0.62, YELLOW, None),
        ("DSC_0138.JPG", "已暂停", "3.1 / 8.2 MB", 0.38, RED, "从断点重试"),
    ]
    for i, (name, status, amount, progress, color, action) in enumerate(cards):
        cy = y + 185 + i * 174
        rr((x + 20, cy, x + 362, cy + 151), 15, PANEL)
        txt(x + 36, cy + 18, name, 14, WHITE, True)
        txt(x + 345, cy + 18, status, 12, color, True, anchor="ra")
        rr((x + 36, cy + 54, x + 346, cy + 62), 4, "#3A3B35")
        rr((x + 36, cy + 54, x + 36 + int(310 * progress), cy + 62), 4, color)
        txt(x + 36, cy + 78, amount, 11, GREY)
        if action:
            rr((x + 36, cy + 103, x + 153, cy + 136), 8, YELLOW)
            txt(x + 94, cy + 119, action, 11, PHONE, True, anchor="mm")
        elif progress == 1.0:
            txt(x + 36, cy + 111, "已保存到 Pictures/ZF Relay", 11, GREEN)
        else:
            txt(x + 36, cy + 111, "4 MiB 分块 · 保留断点", 11, GREY)
    bottom_nav(x, y, 1)

# Artboard heading
txt(38, 28, "ZF RELAY", 34, INK, True)
txt(38, 72, "Android UI 视觉原型  /  连接 · 图库 · 传输", 17, MUTED)
txt(1300, 45, "V0.1  /  非真机截图", 13, MUTED, anchor="ra")

for x, label, subtitle, renderer in [
    (35, "01  连接", "连接方式与相机热点引导", connection_screen),
    (470, "02  图库", "双列照片流与原图下载", gallery_screen),
    (905, "03  传输", "进度、完成和断点续传", transfer_screen),
]:
    sx, sy = phone_shell(x, 125)
    renderer(sx, sy)
    txt(x + 4, 972, label, 18, INK, True)
    txt(x + 86, 975, subtitle, 14, MUTED)

out = "/Users/chrisz/Documents/projects/nikon-connect/ui-prototype/zf-relay-ui-prototype.png"
im.save(out, optimize=True)
print(out)

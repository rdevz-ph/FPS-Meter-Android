import os
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

# 2-Color Palette:
# Background: Deep Obsidian Dark Charcoal (#0F141C)
# Accent: Electric Performance Mint (#00E676)
BG_COLOR = (15, 20, 28, 255)
ACCENT_COLOR = (0, 230, 118, 255)
TRANSPARENT = (0, 0, 0, 0)

REPO_ROOT = Path(__file__).resolve().parent.parent
ICON_PNG = REPO_ROOT / "icon" / "icon.png"
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"

def draw_tapered_needle(draw, cx, cy, angle_deg, length, base_width, tip_width, color):
    rad = math.radians(angle_deg)
    perp_rad = rad + math.pi / 2
    
    tail_len = base_width * 0.70
    tx1 = cx - tail_len * math.cos(rad) + (base_width * 0.35) * math.cos(perp_rad)
    ty1 = cy - tail_len * math.sin(rad) + (base_width * 0.35) * math.sin(perp_rad)
    tx2 = cx - tail_len * math.cos(rad) - (base_width * 0.35) * math.cos(perp_rad)
    ty2 = cy - tail_len * math.sin(rad) - (base_width * 0.35) * math.sin(perp_rad)
    
    tx = cx + length * math.cos(rad)
    ty = cy + length * math.sin(rad)
    
    bx1 = cx + (base_width / 2) * math.cos(perp_rad)
    by1 = cy + (base_width / 2) * math.sin(perp_rad)
    bx2 = cx - (base_width / 2) * math.cos(perp_rad)
    by2 = cy - (base_width / 2) * math.sin(perp_rad)
    
    polygon = [(tx1, ty1), (bx1, by1), (tx, ty), (bx2, by2), (tx2, ty2)]
    draw.polygon(polygon, fill=color)

def generate_centered_foreground(size=2048):
    raw_img = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(raw_img)
    
    cx = size / 2
    cy = size / 2
    
    # 1. Speedometer Arc
    r_center = size * 0.210
    stroke_w = int(size * 0.038)
    
    start_angle = 135
    end_angle = 405
    
    r_box = r_center + stroke_w / 2
    bbox = (cx - r_box, cy - r_box, cx + r_box, cy + r_box)
    draw.arc(bbox, start=start_angle, end=end_angle, fill=ACCENT_COLOR, width=stroke_w)
    
    # Rounded end caps for arc
    for ang in [start_angle, end_angle]:
        ar = math.radians(ang)
        px = cx + r_center * math.cos(ar)
        py = cy + r_center * math.sin(ar)
        cap_r = stroke_w / 2
        draw.ellipse((px - cap_r, py - cap_r, px + cap_r, py + cap_r), fill=ACCENT_COLOR)
        
    # 2. Speed Ticks (7 ticks)
    num_ticks = 7
    for i in range(num_ticks):
        t = i / (num_ticks - 1)
        angle_deg = start_angle + t * (end_angle - start_angle)
        angle_rad = math.radians(angle_deg)
        
        is_major = (i % 2 == 0) or (i == num_ticks - 1)
        tick_len = size * 0.038 if is_major else size * 0.022
        tick_w = int(size * 0.017) if is_major else int(size * 0.010)
        
        r1 = r_center + stroke_w / 2 + size * 0.016
        r2 = r1 + tick_len
        
        x1 = cx + r1 * math.cos(angle_rad)
        y1 = cy + r1 * math.sin(angle_rad)
        x2 = cx + r2 * math.cos(angle_rad)
        y2 = cy + r2 * math.sin(angle_rad)
        
        draw.line([(x1, y1), (x2, y2)], fill=ACCENT_COLOR, width=tick_w)
        cr = tick_w / 2
        draw.ellipse((x1 - cr, y1 - cr, x1 + cr, y1 + cr), fill=ACCENT_COLOR)
        draw.ellipse((x2 - cr, y2 - cr, x2 + cr, y2 + cr), fill=ACCENT_COLOR)
        
    # 3. Tachometer Needle
    needle_angle = 330
    needle_len = size * 0.175
    base_w = size * 0.038
    draw_tapered_needle(draw, cx, cy, needle_angle, needle_len, base_w, size * 0.008, ACCENT_COLOR)
    
    # 4. Center Hub
    hub_r = size * 0.048
    draw.ellipse((cx - hub_r, cy - hub_r, cx + hub_r, cy + hub_r), fill=ACCENT_COLOR)
    hub_inner = size * 0.021
    draw.ellipse((cx - hub_inner, cy - hub_inner, cx + hub_inner, cy + hub_inner), fill=BG_COLOR)
    
    # 5. Bold FPS Typography
    font_path = "C:/Windows/Fonts/ariblk.ttf"
    if not os.path.exists(font_path):
        font_path = "C:/Windows/Fonts/segoeuib.ttf"
    font = ImageFont.truetype(font_path, int(size * 0.118))
    
    letters = ["F", "P", "S"]
    letter_imgs = []
    total_w = 0
    letter_spacing = int(size * 0.020)
    
    for l in letters:
        bbox_l = draw.textbbox((0, 0), l, font=font)
        lw = bbox_l[2] - bbox_l[0]
        lh = bbox_l[3] - bbox_l[1]
        letter_imgs.append((l, lw, lh, bbox_l[0], bbox_l[1]))
        total_w += lw
    total_w += letter_spacing * (len(letters) - 1)
    
    cur_x = cx - total_w / 2
    text_y = cy + size * 0.165
    for l, lw, lh, x_off, y_off in letter_imgs:
        draw.text((cur_x - x_off, text_y - y_off), l, font=font, fill=ACCENT_COLOR)
        cur_x += lw + letter_spacing

    # 6. Geometric auto-centering in safe zone (60% canvas size)
    bbox_content = raw_img.getbbox()
    content_w = bbox_content[2] - bbox_content[0]
    content_h = bbox_content[3] - bbox_content[1]
    
    target_content_size = size * 0.60
    scale = target_content_size / max(content_w, content_h)
    
    cropped = raw_img.crop(bbox_content)
    new_w = int(content_w * scale)
    new_h = int(content_h * scale)
    resized_content = cropped.resize((new_w, new_h), Image.Resampling.LANCZOS)
    
    final_canvas = Image.new("RGBA", (size, size), TRANSPARENT)
    paste_x = int((size - new_w) / 2)
    paste_y = int((size - new_h) / 2)
    final_canvas.paste(resized_content, (paste_x, paste_y), resized_content)
    
    return final_canvas

def create_legacy_icon(fg_master, size, is_round=False):
    base = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(base)
    
    if is_round:
        draw.ellipse((0, 0, size, size), fill=BG_COLOR)
    else:
        radius = int(size * 0.22)
        draw.rounded_rectangle((0, 0, size, size), radius=radius, fill=BG_COLOR)
        
    fg_scaled = fg_master.resize((size, size), Image.Resampling.LANCZOS)
    base.alpha_composite(fg_scaled)
    return base

def generate_and_deploy_all():
    fg_master = generate_centered_foreground(2048)
    
    # 1. Output icon/icon.png (512x512 high-res square icon)
    ICON_PNG.parent.mkdir(parents=True, exist_ok=True)
    sq_512 = create_legacy_icon(fg_master, 512, is_round=False)
    sq_512.save(ICON_PNG, "PNG")
    print(f"Saved: {ICON_PNG}")
    
    # 2. Output all Android mipmap buckets
    densities = {
        "mipmap-mdpi": (48, 108),
        "mipmap-hdpi": (72, 162),
        "mipmap-xhdpi": (96, 216),
        "mipmap-xxhdpi": (144, 324),
        "mipmap-xxxhdpi": (192, 432),
    }
    
    for folder, (icon_sz, fg_sz) in densities.items():
        folder_path = RES_DIR / folder
        folder_path.mkdir(parents=True, exist_ok=True)
        
        # Square legacy launcher icon
        sq_img = create_legacy_icon(fg_master, icon_sz, is_round=False)
        sq_img.save(folder_path / "ic_launcher.webp", "WEBP", quality=95)
        
        # Round legacy launcher icon
        rd_img = create_legacy_icon(fg_master, icon_sz, is_round=True)
        rd_img.save(folder_path / "ic_launcher_round.webp", "WEBP", quality=95)
        
        # Adaptive icon foreground
        fg_sz_img = fg_master.resize((fg_sz, fg_sz), Image.Resampling.LANCZOS)
        fg_sz_img.save(folder_path / "ic_launcher_foreground.png", "PNG")
        
        print(f"Generated {folder}: {icon_sz}x{icon_sz} (sq/rd) and {fg_sz}x{fg_sz} (fg)")

if __name__ == "__main__":
    generate_and_deploy_all()

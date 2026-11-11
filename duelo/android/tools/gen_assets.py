"""Genera los sonidos (WAV) y el icono del juego. Uso: python3 tools/gen_assets.py"""
import math, os, random, struct, wave
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RATE = 22050

def write_wav(name, samples):
    path = os.path.join(ROOT, "res", "raw", name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 30000)) for s in samples))

def tone(f0, f1, dur, vol=0.6, shape="square", decay=8.0):
    out, phase = [], 0.0
    n = int(RATE * dur)
    for i in range(n):
        t = i / n
        f = f0 + (f1 - f0) * t
        phase += 2 * math.pi * f / RATE
        if shape == "square":
            v = 1.0 if math.sin(phase) > 0 else -1.0
            v *= 0.5
        elif shape == "tri":
            v = 2 / math.pi * math.asin(math.sin(phase))
        else:
            v = math.sin(phase)
        env = min(1.0, i / 60) * math.exp(-decay * t)
        out.append(v * env * vol)
    return out

random.seed(7)
write_wav("hit", tone(520, 760, 0.07, 0.55, "square", 5))
write_wav("edge", tone(900, 1500, 0.11, 0.6, "square", 4) )
write_wav("wall", tone(300, 260, 0.05, 0.45, "tri", 6))
write_wav("serve", tone(400, 900, 0.12, 0.5, "tri", 3))
write_wav("point", tone(660, 110, 0.45, 0.6, "square", 2.5))
win = []
for f in (523, 659, 784, 1046):
    win += tone(f, f, 0.13, 0.5, "square", 1.5)
win += tone(1046, 1046, 0.35, 0.5, "tri", 2)
write_wav("win", win)

# Icono: dos barras neón y una pelota
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(bg)
d.rounded_rectangle((0, 0, S - 1, S - 1), radius=110, fill=(10, 11, 24, 255))
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
g = ImageDraw.Draw(glow)
g.rounded_rectangle((126, 92, 386, 128), radius=18, fill=(0, 229, 255, 255))
g.rounded_rectangle((126, 384, 386, 420), radius=18, fill=(255, 46, 136, 255))
g.ellipse((300, 222, 368, 290), fill=(255, 255, 255, 255))
blur = glow.filter(ImageFilter.GaussianBlur(18))
img = Image.alpha_composite(bg, blur)
img = Image.alpha_composite(img, blur)
d2 = ImageDraw.Draw(img)
for y in range(150, 370, 28):
    d2.rectangle((248, y, 264, y + 12), fill=(255, 255, 255, 60))
img = Image.alpha_composite(img, glow)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=110, fill=255)
img.putalpha(mask)
for dens, px in sizes.items():
    os.makedirs(os.path.join(ROOT, "res", "mipmap-" + dens), exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(os.path.join(ROOT, "res", "mipmap-" + dens, "ic_launcher.png"))
img.save(os.path.join(ROOT, "..", "icon-512.png"))
print("ok")

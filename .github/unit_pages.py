"""The website's unit links (bfcompanion.app/u/<id>, what the share button gives): site/u/<id>.html for every unit, a
tiny page whose title, description and picture show in a chat's link preview (Discord, WhatsApp, X) and that opens the
unit's page in the guide. Run by the Website workflow on the files from the release's APK.
Usage: python unit_pages.py <site dir> <base url>"""
import html, json, os, sys

SITE, BASE = sys.argv[1], sys.argv[2].rstrip("/")
U = json.load(open(os.path.join(SITE, "data", "units.json"), encoding="utf-8"))
OUT = os.path.join(SITE, "u")
os.makedirs(OUT, exist_ok=True)
PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} · BF Companion</title>
<meta name="description" content="{desc}">
<meta property="og:type" content="website"><meta property="og:site_name" content="BF Companion">
<meta property="og:title" content="{title}"><meta property="og:description" content="{desc}">
<meta property="og:url" content="{base}/u/{id}"><meta property="og:image" content="{base}/{image}">
<meta name="twitter:card" content="summary"><meta name="theme-color" content="#161922">
<link rel="canonical" href="{base}/u/{id}">
<meta http-equiv="refresh" content="0; url=/#/unit/{id}">
<script>location.replace("/#/unit/{id}")</script>
</head><body style="background:#0b0a14;color:#fff;font:16px Arial,sans-serif;padding:24px">
<a href="/#/unit/{id}" style="color:#ffd45c">{title} on BF Companion</a>
</body></html>
"""
n = 0
for uid, u in U.items():
    name, rarity, element, cost = u[0], u[1], u[2], u[3]
    hp, atk, df, rec = u[8:12]
    stars = "Omni" if rarity == 8 else f"{rarity}★"
    desc = (f"{stars} {element} unit, cost {cost}. Max HP {hp:,}, ATK {atk:,}, DEF {df:,}, REC {rec:,}. "
            "Skills, SP builds and where to get it in Brave Frontier Revived.") if name else ""
    art = f"thumbs/a/{uid}.webp"
    image = art if os.path.exists(os.path.join(SITE, art)) else f"thumbs/u/{uid}.webp"
    with open(os.path.join(OUT, f"{uid}.html"), "w", encoding="utf-8") as f:
        f.write(PAGE.format(title=html.escape(name or "BF Companion"), desc=html.escape(desc), base=BASE, id=uid, image=image))
    n += 1
print(f"{n} unit pages in {OUT}")

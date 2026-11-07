"""assets/www/data/version.json for the build: the app's version and build day (its About sheet), and two fingerprints a
data update is checked against (MainActivity.check): "code", the app around the page (the Java, the manifest without
its version numbers, the resources), and "thumbs", every picture in assets/www/thumbs. A release whose fingerprints
match an installed app's only needs its page, data and static files (BF-Companion-www.zip), not the whole APK.
First it puts the share icons in (share_icons). Run by build_apk.bat from the android folder."""
import datetime, hashlib, json, os, re, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))


def tree_hash(root, h, text=None, skip=()):
    """skip: folders (relative to root, e.g. "uhd") left out"""
    for d, dirs, files in os.walk(root):
        dirs[:] = sorted(x for x in dirs if os.path.relpath(os.path.join(d, x), root).replace(os.sep, "/") not in skip)
        for f in sorted(files):
            p = os.path.join(d, f)
            h.update(os.path.relpath(p, root).replace(os.sep, "/").encode() + b"\0")
            h.update(text(p) if text else open(p, "rb").read())


def fingerprints():
    manifest = open(os.path.join(HERE, "AndroidManifest.xml"), encoding="utf-8").read()
    code = hashlib.sha256(re.sub(r'android:version(Code|Name)="[^"]*"', "", manifest).encode())
    tree_hash(os.path.join(HERE, "src"), code)
    tree_hash(os.path.join(HERE, "res"), code)
    thumbs = hashlib.sha256()
    tree_hash(os.path.join(HERE, "assets", "www", "thumbs"), thumbs)
    return code.hexdigest(), thumbs.hexdigest()


def share_icons():
    """The sharp unit icons (204 px; the game's are 102) for the pull picture you share, and only for it (the app's
    lists keep the small ones): assets/www/thumbs/uhd, the website's (site/site_art.py) taken from
    dist/site-pictures.zip, for the units the app has. Without the zip (the repo's copy of this) it stays as it is."""
    src, www = os.path.join(HERE, "..", "dist", "site-pictures.zip"), os.path.join(HERE, "assets", "www", "thumbs")
    if not os.path.exists(src):
        return
    have, out = {f[:-5] for f in os.listdir(os.path.join(www, "u"))}, os.path.join(www, "uhd")
    os.makedirs(out, exist_ok=True)
    with zipfile.ZipFile(src) as z:
        want = {n[4:]: n for n in z.namelist() if n.startswith("uhd/") and n.endswith(".webp") and n[4:-5] in have}
        for f in os.listdir(out):
            if f not in want:
                os.remove(os.path.join(out, f))
        for f, n in want.items():
            data, p = z.read(n), os.path.join(out, f)
            if not os.path.exists(p) or open(p, "rb").read() != data:
                open(p, "wb").write(data)
    print("share icons:", len(want))


if __name__ == "__main__":
    share_icons()
    version = re.search(r'versionName="([^"]+)"', open(os.path.join(HERE, "AndroidManifest.xml"), encoding="utf-8").read())[1]
    code, thumbs = fingerprints()
    with open(os.path.join(HERE, "assets", "www", "data", "version.json"), "w", encoding="utf-8") as f:
        json.dump({"version": version, "built": datetime.date.today().isoformat(), "code": code, "thumbs": thumbs}, f)
    print("version.json:", version, "code", code[:12], "thumbs", thumbs[:12])

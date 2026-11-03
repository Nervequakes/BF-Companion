"""assets/www/data/version.json for the build: the app's version and build day (its About sheet), and two fingerprints a
data update is checked against (MainActivity.check): "code", the app around the page (the Java, the manifest without
its version numbers, the resources), and "thumbs", every picture in assets/www/thumbs. A release whose fingerprints
match an installed app's only needs its page, data and static files (BF-Companion-www.zip), not the whole APK.
Run by build_apk.bat from the android folder."""
import datetime, hashlib, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))


def tree_hash(root, h, text=None):
    for d, dirs, files in os.walk(root):
        dirs.sort()
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


if __name__ == "__main__":
    version = re.search(r'versionName="([^"]+)"', open(os.path.join(HERE, "AndroidManifest.xml"), encoding="utf-8").read())[1]
    code, thumbs = fingerprints()
    with open(os.path.join(HERE, "assets", "www", "data", "version.json"), "w", encoding="utf-8") as f:
        json.dump({"version": version, "built": datetime.date.today().isoformat(), "code": code, "thumbs": thumbs}, f)
    print("version.json:", version, "code", code[:12], "thumbs", thumbs[:12])

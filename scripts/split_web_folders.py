"""Move the app into web/users/, leaving five folders at the top of web/.

Run once, by .github/workflows/split-web.yml. It does the move, points the build pipeline at the
new place, rebuilds the site, and then walks every reference in the built tree. If anything does
not resolve it exits non-zero and writes nothing - so a broken path cannot reach main.

The shape it produces:

    web/assets/          shared: the mark, the icons, the social image
    web/authentication/  shared: the sign-in code both sides use
    web/branding/        shared
    web/users/           the app
    web/developers/      the public API page, the docs, the console

The published address does not change. build_site.py copies users/ plus the three shared folders
into /app/, which is the shape the app had when all of it sat in web/ - so every relative path
inside it keeps working and /app/ is still /app/.
"""

from __future__ import annotations

import pathlib
import re
import shutil
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
APP = WEB / "users"
SITE = ROOT / "site"

APP_ITEMS = [
    "index.html", "style.css", "config.js", "update.json", "robots.txt", "sitemap.xml",
    "favicon.ico", "BingSiteAuth.xml", "bottom-nav-svg.css", "README.md", "database.rules.json",
    "home", "menu", "settings", "lobby", "vibe", "legal", "login", "signup", "shared",
]


def die(message: str) -> None:
    print(f"split_web_folders: {message}", file=sys.stderr)
    sys.exit(1)


def move() -> None:
    if APP.exists() and (APP / "index.html").exists():
        print("split_web_folders: web/users/ is already the app; nothing to move")
        return

    APP.mkdir(exist_ok=True)
    for name in APP_ITEMS:
        source = WEB / name
        if source.exists():
            shutil.move(str(source), str(APP / name))

    for item in WEB.glob("*.txt"):
        shutil.move(str(item), str(APP / item.name))

    print("split_web_folders: moved the app into web/users/")


def repoint() -> None:
    # the splash mark is published at the site root, so the app asks for it absolutely
    index = APP / "index.html"
    text = index.read_text(encoding="utf-8")
    index.write_text(text.replace('src="assets/mark.png"', 'src="/assets/mark.png"'), encoding="utf-8")

    # the developer pages sit under /app/ too, so their mark is the same root copy
    root = WEB / "developers"
    for page in root.rglob("index.html"):
        body = page.read_text(encoding="utf-8")
        depth = len(page.relative_to(root).parts) - 1
        page.write_text(body.replace("../" * (depth + 1) + "assets/mark.png", "/assets/mark.png"),
                        encoding="utf-8")
    print("split_web_folders: the mark is asked for at the root, where it is published")


def patch_build_site() -> None:
    path = ROOT / "scripts" / "build_site.py"
    s = path.read_text(encoding="utf-8")
    if 'APP = WEB / "users"' in s:
        return

    s = s.replace(
        'WEB = ROOT / "web"',
        'WEB = ROOT / "web"\n'
        '# web/ is split by who it is for: users/ is the app, developers/ is the console, and\n'
        '# assets/, authentication/ and branding/ are shared by both.\n'
        'APP = WEB / "users"\n'
        'SHARED = ("authentication", "branding", "developers")')
    s = s.replace('WEB / "index.html", WEB / "update.json"', 'APP / "index.html", APP / "update.json"')
    s = s.replace('[WEB / "legal" / f"{slug}.html" for slug in LEGAL]',
                  '[APP / "legal" / f"{slug}.html" for slug in LEGAL]')
    s = s.replace('WEB / "favicon.ico"]', 'APP / "favicon.ico"]')
    s = s.replace(
        '    shutil.copytree(WEB, SITE / "app")',
        '    shutil.copytree(APP, SITE / "app")\n'
        '    for shared in SHARED:\n'
        '        shutil.copytree(WEB / shared, SITE / "app" / shared)')
    s = s.replace('for item in WEB.glob("*.txt"):', 'for item in APP.glob("*.txt"):')
    s = s.replace('(WEB / "BingSiteAuth.xml")', '(APP / "BingSiteAuth.xml")')
    s = s.replace('WEB / "BingSiteAuth.xml", SITE', 'APP / "BingSiteAuth.xml", SITE')
    s = s.replace('shutil.copy2(WEB / "favicon.ico"', 'shutil.copy2(APP / "favicon.ico"')
    s = s.replace('LOGOS = ["logo-1024.png", "logo-1024-white.png"]',
                  'LOGOS = ["logo-1024.png", "logo-1024-white.png", "mark.png"]')
    path.write_text(s, encoding="utf-8")
    print("split_web_folders: build_site.py now builds /app/ from web/users/")


def patch_legal_pages() -> None:
    path = ROOT / "scripts" / "legal_pages.py"
    s = path.read_text(encoding="utf-8")
    s = s.replace('ROOT / "web" / "menu"', 'ROOT / "web" / "users" / "menu"')
    s = s.replace('ROOT / "web" / "legal"', 'ROOT / "web" / "users" / "legal"')
    s = s.replace('`web/menu/<slug>/index.html`', '`web/users/menu/<slug>/index.html`')
    s = s.replace('  web/legal/<slug>.html', '  web/users/legal/<slug>.html')
    s = s.replace('in web/menu/<slug>/index.html', 'in web/users/menu/<slug>/index.html')
    path.write_text(s, encoding="utf-8")
    print("split_web_folders: legal_pages.py reads and writes inside web/users/")


def patch_workflow() -> None:
    path = ROOT / ".github" / "workflows" / "pages.yml"
    s = path.read_text(encoding="utf-8")
    if "web/users/index.html" in s:
        return
    s = s.replace(
        "          test -f web/index.html\n          test -f web/update.json",
        "          test -f web/users/index.html\n          test -f web/users/update.json\n"
        "          test -d web/developers\n          test -d web/assets")
    path.write_text(s, encoding="utf-8")
    print("split_web_folders: the publish check looks in web/users/")


def check() -> None:
    import subprocess

    subprocess.run([sys.executable, "scripts/legal_pages.py"], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)
    subprocess.run([sys.executable, "scripts/build_site.py"], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)

    if not (SITE / "app" / "index.html").exists():
        die("/app/index.html was not built")
    for shared in ("authentication", "branding", "developers"):
        if not (SITE / "app" / shared).is_dir():
            die(f"/app/{shared} was not built")

    missing = []
    checked = 0
    for f in SITE.rglob("*"):
        if f.is_dir() or f.suffix not in (".html", ".css"):
            continue
        text = f.read_text(encoding="utf-8", errors="replace")
        refs = re.findall(r'(?:href|src)="([^"]+)"', text) + re.findall(r"url\(['\"]?([^'\")]+)", text)
        for ref in refs:
            ref = ref.strip()
            if not ref or ref.startswith(("http://", "https://", "mailto:", "#", "data:", "tel:")):
                continue
            ref = urllib.parse.unquote(ref.split("#")[0].split("?")[0])
            if not ref:
                continue
            target = (SITE / ref.lstrip("/")) if ref.startswith("/") else (f.parent / ref)
            if ref.endswith("/"):
                target = target / "index.html"
            checked += 1
            if not target.exists():
                missing.append(f"{f.relative_to(SITE)} -> {ref}")

    if missing:
        for line in missing[:20]:
            print("  missing:", line, file=sys.stderr)
        die(f"{len(missing)} references do not resolve; nothing was committed")

    print(f"split_web_folders: {checked} references checked, none missing")


def main() -> None:
    for name in ("assets", "authentication", "branding", "developers"):
        if not (WEB / name).is_dir():
            die(f"web/{name} is missing")

    move()
    repoint()
    patch_build_site()
    patch_legal_pages()
    patch_workflow()
    check()

    print("web/ now holds: " + ", ".join(sorted(p.name for p in WEB.iterdir() if p.is_dir())))


if __name__ == "__main__":
    main()

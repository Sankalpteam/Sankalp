#!/usr/bin/env python3
"""Assemble the folder that GitHub Pages publishes.

Pages serves one directory, and until now that directory was `web/` - which is why the app
itself was the site's front page. The landing page needs the root, the app needs its own
address, and the four legal documents have to stay exactly where the company site links to
them. So the published tree is built, not committed:

  site/index.html      the landing page            (from landing/)
  site/style.css       its stylesheet              (from landing/)
  site/app/            the whole app               (from web/, relative paths intact)
  site/legal/*.html    the four documents          (from web/legal/, one copy)
  site/assets/         logo and icons              (from web/assets/)
  site/update.json     the version manifest        (from web/)
  site/404.html        forwards old app paths to /app/
  site/sitemap.xml, robots.txt, favicon.ico, CNAME, BingSiteAuth.xml, <indexnow-key>.txt

`site/` is generated on every deploy and is not in the repository.
"""

from __future__ import annotations

import datetime
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
# web/ is split by who it is for: users/ is the app, developers/ is the console, and
# assets/, authentication/ and branding/ are shared by both.
APP = WEB / "users"
SHARED = ("authentication", "branding", "developers")
LANDING = ROOT / "landing"
SITE = ROOT / "site"

DOMAIN = "sankalp.indoworkspace.in"
LEGAL = ["privacy-policy", "terms-of-use", "data-privacy", "security"]
ICONS = ["icon-48.png", "icon-96.png", "icon-180.png", "icon-192.png", "icon-512.png"]
LOGOS = ["logo-1024.png", "logo-1024-white.png", "mark.png"]

SITEMAP = """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
{urls}
</urlset>
"""

ROBOTS = """User-agent: *
Allow: /
Disallow: /app/

Sitemap: https://{domain}/sitemap.xml
"""

NOT_FOUND = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Sankalp — page moved</title>
<meta name="robots" content="noindex, follow">
<script>
  // The app used to live at the root, so old bookmarks and shared links point at paths like
  // /settings/update/ that are now under /app/. GitHub Pages cannot send a 301, and this is
  // the closest thing it can do: read the path, and if it is one of the app's own folders,
  // send the visitor to the same place under /app/.
  (function () {
    var folders = ['authentication', 'home', 'lobby', 'menu', 'settings', 'shared', 'vibe', 'branding'];
    var path = location.pathname.replace(/^\\/+/, '');
    var first = path.split('/')[0];
    if (folders.indexOf(first) !== -1) {
      location.replace('/app/' + path + location.search + location.hash);
    }
  })();
</script>
<style>
  body { margin:0; min-height:100vh; display:grid; place-items:center; background:#f7f5f0;
         font-family:system-ui, sans-serif; color:#121b2e; text-align:center; padding:24px; }
  h1 { font-size:24px; margin:0 0 10px; }
  p { color:#3d4a63; margin:0 0 22px; }
  a { display:inline-block; background:#5e2ce2; color:#fff; text-decoration:none;
      padding:13px 24px; border-radius:999px; font-weight:600; }
</style>
</head>
<body>
  <div>
    <h1>That page has moved</h1>
    <p>Sankalp lives at <strong>sankalp.indoworkspace.in</strong>, and the app is now under <code>/app/</code>.</p>
    <a href="/">Go to Sankalp</a>
  </div>
</body>
</html>
"""


def die(message: str) -> None:
    print(f"build_site: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(paths: list[pathlib.Path]) -> None:
    missing = [str(p.relative_to(ROOT)) for p in paths if not p.exists()]
    if missing:
        die("missing required input: " + ", ".join(missing))



# The console's rail, written into every console page at build time.
#
# It was a script the pages fetched, which is one request that a browser can serve from its cache -
# and did: some screens drew eight items and some twelve, depending on which copy of the script that
# page had been given. A rail that is part of the page cannot be stale, and there is still only one
# list, here.
CONSOLE_RAIL = [
    ("console", "./", "Overview"),
    ("keys", "keys/", "API keys"),
    ("usage", "usage/", "Usage"),
    ("models", "models/", "Models"),
    ("playground", "playground/", "Playground"),
    ("logs", "logs/", "Logs"),
    ("limits", "limits/", "Rate limits"),
    ("webhooks", "webhooks/", "Webhooks"),
    ("projects", "projects/", "Projects"),
    ("team", "team/", "Team"),
    ("settings", "settings/", "Settings"),
    ("wallet", "wallet/", "Wallet"),
]


def paint_console_rail() -> int:
    """Fill the rail placeholder in every console page, and drop the script that used to."""
    console = SITE / "app" / "developers" / "console"
    if not console.exists():
        return 0

    painted = 0

    for page in sorted(console.rglob("index.html")):
        html = page.read_text(encoding="utf-8")
        if 'data-console-nav' not in html:
            continue

        depth = "" if page.parent == console else "../"
        current = "console" if page.parent == console else page.parent.name

        lines = []
        for item_id, href, label in CONSOLE_RAIL:
            active = ' class="on"' if item_id == current else ""
            lines.append(f'          <a{active} href="{depth}{href}"><span class="dot"></span>{label}</a>')

        html = html.replace(
            '<div class="nav" data-console-nav></div>',
            '<div class="nav">\n' + "\n".join(lines) + "\n        </div>",
            1,
        )
        # the script that filled it is no longer needed
        html = re.sub(r'\s*<script src="(?:\.\./)?nav\.js[^"]*"></script>', "", html)

        page.write_text(html, encoding="utf-8")
        painted += 1

    return painted


def main() -> None:
    require(
        [LANDING / "index.html", LANDING / "style.css", APP / "index.html", APP / "update.json"]
        + [APP / "legal" / f"{slug}.html" for slug in LEGAL]
        + [APP / "favicon.ico"]
    )

    if SITE.exists():
        shutil.rmtree(SITE)
    SITE.mkdir()

    # 1. the landing page at the root
    shutil.copy2(LANDING / "index.html", SITE / "index.html")
    shutil.copy2(LANDING / "style.css", SITE / "style.css")

    # 2. the app, whole, so every relative path inside it keeps working
    shutil.copytree(APP, SITE / "app")
    for shared in SHARED:
        shutil.copytree(WEB / shared, SITE / "app" / shared)

    painted = paint_console_rail()

    # The same artwork, inside /app/ as well as at the root.
    #
    # The app's own scripts ask for it relatively: shared/branding.js builds
    # <page>/assets/mark.png from its own script URL, so at /app/shared/branding.js that is
    # /app/assets/mark.png. Publishing the files only at /assets/ left the logo a broken image
    # on every screen that draws it. Two copies of five small files is the cheap side of that
    # trade; the alternative is a path built in three places.
    shutil.copytree(WEB / "assets", SITE / "app" / "assets")

    # 3. one copy of the legal documents, at the address the company site links to
    shutil.move(str(SITE / "app" / "legal"), str(SITE / "legal"))

    # 4. the version manifest, readable from both addresses (see the module docstring of
    #    web/settings/update/script.js: the app fetches it relatively, so the copy inside
    #    /app/ is the one that matters, and the root copy keeps old links working)
    shutil.copy2(SITE / "app" / "update.json", SITE / "update.json")

    # 5. the mark and the icons, at the root, because the app's own index.html links to them
    #    absolutely (/assets/icon-48.png). The social preview lives in landing/assets and is
    #    copied here too, because og:image points at /assets/og-image.png.
    assets = SITE / "assets"
    assets.mkdir()
    landing_assets = LANDING / "assets"
    if landing_assets.exists():
        for item in landing_assets.iterdir():
            if item.is_file():
                shutil.copy2(item, assets / item.name)
    for name in LOGOS + ICONS:
        source = WEB / "assets" / name
        if source.exists():
            shutil.copy2(source, assets / name)
        else:
            print(f"build_site: warning - web/assets/{name} is missing", file=sys.stderr)
    shutil.copy2(APP / "favicon.ico", SITE / "favicon.ico")

    # 5b. the two files that only work at the root. Both were left behind when the app moved
    #     to /app/, and both fail silently if they are one folder too deep: the IndexNow key
    #     is fetched at /<key>.txt to prove we control the host, and BingSiteAuth.xml is
    #     fetched at /BingSiteAuth.xml to prove the same thing to Bing.
    for item in APP.glob("*.txt"):
        if len(item.stem) == 32 and all(c in "0123456789abcdef" for c in item.stem):
            shutil.copy2(item, SITE / item.name)
    if (APP / "BingSiteAuth.xml").exists():
        shutil.copy2(APP / "BingSiteAuth.xml", SITE / "BingSiteAuth.xml")

    # 5c. the app's own copy of these two would be a second, stale answer to the same question
    for stale in ("sitemap.xml", "robots.txt"):
        path = SITE / "app" / stale
        if path.exists():
            path.unlink()

    # 6. the rest of the published root
    today = datetime.date.today().isoformat()
    urls = [f"  <url><loc>https://{DOMAIN}/</loc><lastmod>{today}</lastmod>"
            f"<changefreq>weekly</changefreq><priority>1.0</priority></url>"]
    urls += [
        f"  <url><loc>https://{DOMAIN}/legal/{slug}.html</loc><lastmod>{today}</lastmod>"
        f"<changefreq>yearly</changefreq><priority>{priority}</priority></url>"
        for slug, priority in zip(LEGAL, ("0.6", "0.6", "0.5", "0.5"))
    ]
    (SITE / "sitemap.xml").write_text(SITEMAP.format(urls="\n".join(urls)))
    (SITE / "robots.txt").write_text(ROBOTS.format(domain=DOMAIN))
    (SITE / "404.html").write_text(NOT_FOUND)
    (SITE / "CNAME").write_text(DOMAIN + "\n")

    print(f"build_site: site/ ready — landing, app, {len(LEGAL)} legal pages, sitemap, robots")


if __name__ == "__main__":
    main()

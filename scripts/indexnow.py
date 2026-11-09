#!/usr/bin/env python3
"""Tell Bing, Yandex, Seznam and Naver that the site changed.

Google is not the only index that matters in India, and the others do not crawl on their own
schedule the way Google does - a new domain can sit unnoticed for weeks. IndexNow is the one
protocol all of them read, and it costs one POST:

    python3 scripts/indexnow.py            # submit every URL in the sitemap
    python3 scripts/indexnow.py --dry-run  # print what would be sent

The key is a file served at the site root, named after the key and containing the key. That
is how the engine proves the sender controls the host. It lives in web/<key>.txt and is
published with the rest of the site, so nothing has to be configured anywhere.

Run it from a machine with internet access, after the deploy has finished. Submitting a URL
that is not live yet is refused by the engine, not queued.
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
SITE = "https://sankalp.indoworkspace.in"
ENDPOINT = "https://api.indexnow.org/indexnow"


def key_of() -> str:
    """The key file is the one .txt in web/ whose name is 32 hex characters."""
    for path in (ROOT / "web").glob("*.txt"):
        if len(path.stem) == 32 and all(c in "0123456789abcdef" for c in path.stem):
            return path.stem
    raise SystemExit("no IndexNow key file in web/ - expected a 32-character hex .txt")


def urls() -> list[str]:
    tree = ET.parse(ROOT / "web" / "sitemap.xml")
    return [node.text.strip() for node in tree.iter("{http://www.sitemaps.org/schemas/sitemap/0.9}loc")]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    key = key_of()
    payload = {
        "host": SITE.split("//")[1],
        "key": key,
        "keyLocation": f"{SITE}/{key}.txt",
        "urlList": urls(),
    }

    if args.dry_run:
        print(f"would submit {len(payload['urlList'])} URLs to {ENDPOINT}")
        for url in payload["urlList"]:
            print("  ", url)
        return 0

    import json

    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        ENDPOINT, data=body, headers={"Content-Type": "application/json; charset=utf-8"}
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        print(f"IndexNow answered {response.status} for {len(payload['urlList'])} URLs")

    return 0


if __name__ == "__main__":
    sys.exit(main())

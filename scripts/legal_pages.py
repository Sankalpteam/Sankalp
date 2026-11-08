#!/usr/bin/env python3
"""Build the standalone legal pages from the one copy that is written by hand.

The policy and the terms live in `web/menu/<slug>/index.html` as a *fragment*: a shell with a
title, a close button and the content. That fragment is right inside the web app's menu modal,
where the modal provides the frame - and wrong everywhere else, because a WebView gets the
shell and the close button and no stylesheet at all. That is exactly how the app looked: the
title twice, a stray ×, and the text hugging the left edge.

So this script takes the content out of the fragment and writes a complete page around it:
properly styled, no duplicate title, no close button. Two outputs from one source:

  web/legal/<slug>.html          what the app's WebView loads
  android/assets/legal/<slug>.html   the same page, bundled for offline

Run it before a build. Nothing here should ever be edited by hand.
"""

from __future__ import annotations

import pathlib
import re
import sys

# The four information screens. Each has one job, and none of them repeats another:
#
#   privacy-policy  what we collect and who sees it   (legal)
#   terms-of-use    the rules you agree to            (legal)
#   data-privacy    what is stored and for how long   (product facts)
#   security        how the account is protected      (product facts)
#
# All four are written once, in web/menu/<slug>/index.html, and everything else is generated.
# The description is what a search engine shows under the title. One line each, written to
# answer the question someone typed to get here, not to repeat the heading.
DOCUMENTS = (
    ("privacy-policy", "Privacy Policy",
     "What Indoone collects, who sees it, and how long it is kept."),
    ("terms-of-use", "Terms of Use",
     "The rules for using Indoone, in plain language."),
    ("data-privacy", "Data & Privacy",
     "What Indoone stores, where it is stored, and how to get rid of it."),
    ("security", "Security",
     "How an Indoone account and its one-time codes are protected."),
)

ROOT = pathlib.Path(__file__).resolve().parent.parent

PAGE = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
  <meta name="robots" content="index, follow" />
  <title>{title} · Indoone</title>
  <meta name="description" content="{description}" />
  <link rel="canonical" href="https://indoone.indoworkspace.in/legal/{slug}.html" />

  <meta property="og:type" content="article" />
  <meta property="og:site_name" content="Indoone AI" />
  <meta property="og:title" content="{title} · Indoone" />
  <meta property="og:description" content="{description}" />
  <meta property="og:url" content="https://indoone.indoworkspace.in/legal/{slug}.html" />

  <!-- These four pages were served with robots noindex while the sitemap listed them. That
       is a contradiction: the sitemap asked for an index and the page refused it, and a
       search console reports it as an error. They are public documents, so they are
       indexable, and each one carries its own title, description and breadcrumb - which is
       how one site earns several results instead of one. -->
  <script type="application/ld+json">
    {jsonld}
  </script>
  <style>
    :root {{ color-scheme: light dark; }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      padding: 20px 20px 40px;
      background: #ffffff;
      color: #211d27;
      font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
      -webkit-text-size-adjust: 100%;
    }}
    main {{ max-width: 680px; margin: 0 auto; }}
    h1 {{ margin: 0 0 6px; font-size: 22px; line-height: 1.3; }}
    .sr-only {{
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }}
    h3 {{ margin: 26px 0 6px; font-size: 16px; line-height: 1.4; color: #2c2733; }}
    p {{ margin: 0 0 12px; color: #4a4552; }}
    .stamp {{
      display: inline-block;
      margin: 0 0 18px;
      padding: 5px 10px;
      border-radius: 9px;
      background: #f3edff;
      color: #6330db;
      font-size: 13px;
    }}
    @media (prefers-color-scheme: dark) {{
      body {{ background: #121016; color: #ece8f3; }}
      h1, h3 {{ color: #f4f1f9; }}
      p {{ color: #c3bdcd; }}
      .stamp {{ background: #241a3a; color: #bda6ff; }}
    }}
  </style>
</head>
<body>
<main>
<h1 class="sr-only">{title}</h1>
{stamp}
{content}
</main>
</body>
</html>
"""


def stamp_of(slug: str) -> str:
    """The date the document was last changed, taken from the fragment.

    It sits in the fragment's header, outside the content, so extracting only the content
    dropped it - and a policy with no date is a policy a reader cannot judge.
    """
    text = (ROOT / "web" / "menu" / slug / "index.html").read_text(encoding="utf-8")
    match = re.search(r"Last [Uu]pdated[:\s]+([0-9]{1,2}/[0-9]{1,2}/[0-9]{4})", text)

    if match is None:
        return ""

    return f'<p class="stamp">Last updated {match.group(1)}</p>'


def content_of(slug: str) -> str:
    fragment = ROOT / "web" / "menu" / slug / "index.html"
    text = fragment.read_text(encoding="utf-8")

    match = re.search(r'<div[^>]*class="[a-z-]*content"[^>]*>', text)

    if match is None:
        raise SystemExit(f"no content div in {fragment}")

    # The content div is the last block before the closing tags, so its body runs from the
    # opening tag to the final </div> of the file.
    body = text[match.end():]
    closing = body.rstrip().rfind("</div>")

    if closing == -1:
        raise SystemExit(f"content div in {fragment} is not closed")

    return body[:closing].strip()


def structured_data(slug: str, title: str, description: str) -> str:
    """WebPage plus a breadcrumb, so the result shows Indoone > Legal > Privacy Policy."""
    import json

    url = f"https://indoone.indoworkspace.in/legal/{slug}.html"

    return json.dumps(
        {
            "@context": "https://schema.org",
            "@graph": [
                {
                    "@type": "WebPage",
                    "@id": url,
                    "url": url,
                    "name": f"{title} · Indoone",
                    "description": description,
                    "isPartOf": {"@id": "https://indoone.indoworkspace.in/#website"},
                    "inLanguage": "en",
                },
                {
                    "@type": "BreadcrumbList",
                    "itemListElement": [
                        {"@type": "ListItem", "position": 1, "name": "Indoone",
                         "item": "https://indoone.indoworkspace.in/"},
                        {"@type": "ListItem", "position": 2, "name": title, "item": url},
                    ],
                },
            ],
        },
        ensure_ascii=False,
        indent=2,
    )


def write(path: pathlib.Path, slug: str, title: str, description: str, content: str, stamp: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        PAGE.format(
            title=title,
            slug=slug,
            description=description,
            jsonld=structured_data(slug, title, description),
            stamp=stamp,
            content=content,
        ),
        encoding="utf-8",
    )
    print(f"  {path.relative_to(ROOT)}  ({path.stat().st_size} bytes)")


def main() -> int:
    print("Building standalone legal pages:")
    for slug, title, description in DOCUMENTS:
        content = content_of(slug)
        stamp = stamp_of(slug)
        write(ROOT / "web" / "legal" / f"{slug}.html", slug, title, description, content, stamp)
        write(ROOT / "android" / "assets" / "legal" / f"{slug}.html", slug, title, description, content, stamp)
    return 0


if __name__ == "__main__":
    sys.exit(main())

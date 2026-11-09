# Android branding

The mark is a watercolour brush stroke. It is not a shape that can be redrawn as a vector
without becoming a different mark, so nothing here is a vector source any more.

- `../../branding/sankalp-mark.png` — the artwork, exactly as it was supplied. The single
  source for every icon in the app and on the web.
- `../../scripts/brand_assets.py` — reads that one file and writes every launcher icon,
  splash image, in-app mark, notification silhouette, web icon and the og:image.

Run it after any change to the artwork:

```
python3 scripts/brand_assets.py
```

Do not hand-edit a generated PNG, and do not add a second copy of the mark: the previous
round had three Compose screens and a web script each drawing their own version of the old
logo, and they drifted apart. The runtime resources under `android/src/main/res/` are
generated; the file this README sits in is the only hand-written thing in this folder.

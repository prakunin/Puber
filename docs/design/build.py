#!/usr/bin/env python3
"""Inline a stand's assets so the page opens straight from disk.

Each stand is a folder holding `index.src.html`, an `assets/` directory and the built
`index.html`. The source refers to an asset by the token `__ASSET:name.ext__`, which this
script replaces with a `data:` URI. The result has no external references at all, so it
survives being copied anywhere and does not depend on the Artifact host staying up.

    python3 docs/design/build.py                 # every stand
    python3 docs/design/build.py player-controls # just this one
"""

import base64
import mimetypes
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOKEN = re.compile(r"__ASSET:([A-Za-z0-9._-]+)__")


def build(stand: Path) -> None:
    source = stand / "index.src.html"
    if not source.is_file():
        raise SystemExit(f"{stand.name}: no index.src.html")

    text = source.read_text(encoding="utf-8")
    used: set[str] = set()

    def inline(match: re.Match) -> str:
        name = match.group(1)
        asset = stand / "assets" / name
        if not asset.is_file():
            raise SystemExit(f"{stand.name}: missing asset {name}")
        used.add(name)
        mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
        return f"data:{mime};base64,{base64.b64encode(asset.read_bytes()).decode()}"

    built = TOKEN.sub(inline, text)
    if TOKEN.search(built):
        raise SystemExit(f"{stand.name}: unresolved asset token")

    target = stand / "index.html"
    target.write_text(built, encoding="utf-8")
    print(f"{stand.name}: {target.relative_to(ROOT.parent.parent)} "
          f"({len(built) / 1024:.0f} KB, {len(used)} asset(s))")


def main() -> None:
    names = sys.argv[1:]
    stands = [ROOT / n for n in names] if names else sorted(
        p for p in ROOT.iterdir() if p.is_dir() and (p / "index.src.html").is_file()
    )
    if not stands:
        raise SystemExit("nothing to build")
    for stand in stands:
        build(stand)


if __name__ == "__main__":
    main()

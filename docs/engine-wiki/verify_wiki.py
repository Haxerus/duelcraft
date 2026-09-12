"""Check wiki links, source fingerprints, and public message-index coverage."""

import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote


WIKI = Path(__file__).resolve().parent
LINK = re.compile(r"\[[^\]\n]+\]\(([^)\n]+)\)")


def fingerprint(path):
    data = path.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n")
    return hashlib.sha256(data).hexdigest()


def heading_ids(content):
    seen = {}
    anchors = set()
    for title in re.findall(r"^#{1,6} (.+)$", content, re.MULTILINE):
        slug = re.sub(r"[^\w\- ]", "", title.strip().lower()).replace(" ", "-")
        count = seen.get(slug, 0)
        seen[slug] = count + 1
        anchors.add(f"{slug}-{count}" if count else slug)
    return anchors


def main():
    errors = []
    cited_sources = set()
    link_count = 0
    documents = sorted(WIKI.glob("*.md")) + [WIKI / "llms.txt"]
    for document in documents:
        content = document.read_text(encoding="utf-8")
        for target in LINK.findall(content):
            if re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", target):
                continue
            path_text, _, anchor = unquote(target.strip("<>")).partition("#")
            path = (document.parent / path_text).resolve() if path_text else document
            link_count += 1
            label = f"{document.name}: {target}"
            if not path.is_file():
                errors.append(f"Missing target: {label}")
                continue
            if not path.is_relative_to(WIKI):
                cited_sources.add(path)
            if anchor:
                source = path.read_text(encoding="utf-8-sig")
                line_anchor = re.fullmatch(r"L(\d+)(?:-L?(\d+))?", anchor)
                if line_anchor:
                    start = int(line_anchor[1])
                    end = int(line_anchor[2] or start)
                    if not 1 <= start <= end <= len(source.splitlines()):
                        errors.append(f"Invalid line anchor: {label}")
                elif path.suffix == ".md" and anchor not in heading_ids(source):
                    errors.append(f"Missing heading: {label}")

    manifest = json.loads((WIKI / "sources.json").read_text(encoding="utf-8"))
    recorded = set()
    for entry in manifest["files"]:
        path = (WIKI / entry["path"]).resolve()
        recorded.add(path)
        if not path.is_file():
            errors.append(f"Missing fingerprint source: {entry['path']}")
        elif fingerprint(path) != entry["sha256_lf"]:
            errors.append(f"Changed source; review dependent pages: {entry['path']}")
    for path in sorted(cited_sources - recorded):
        errors.append(f"Cited source absent from manifest: {path}")

    constants = (WIKI / "../../native/ygopro-core/ocgapi_constants.h").read_text()
    expected = set(re.findall(r"^#define\s+(MSG_\w+)\s", constants, re.MULTILINE))
    index = (WIKI / "message-index.md").read_text(encoding="utf-8")
    indexed = set(re.findall(r"\bMSG_\w+\b", index))
    for name in sorted(expected - indexed):
        errors.append(f"Message index missing: {name}")
    if errors:
        print("Wiki verification FAILED:")
        for error in errors:
            print(f"- {error}")
        return 1
    print(
        f"PASS: {len(documents)} documents, {link_count} local links, "
        f"{len(recorded)} source fingerprints, {len(expected)} public message constants."
    )
    print("Structural/provenance checks only; no native build or runtime duel tests performed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""
Deprecated crawl-import shortcut.

The old implementation used hardcoded database credentials and mixed seed data
with crawled data. It is disabled to keep imports source-traceable.

Use scripts/health_resource_import.py for external-import datasets, and build
new crawler-specific importers with dry-run + source metadata before writing.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("import_latest_crawl.py is disabled: use reviewed source-specific importers only.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

"""
Deprecated unsafe seed script.

This file used to insert hand-written health encyclopedia entries. It is now
disabled because the project rule is strict: no fabricated medical data.

Use scripts/health_resource_import.py and reviewed, source-traceable Excel/JSON
exports instead.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("seed_health_encyclopedia.py is disabled: fabricated/hand-written medical seed data is not allowed.")
    print("Use scripts/health_resource_import.py with official source files instead.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

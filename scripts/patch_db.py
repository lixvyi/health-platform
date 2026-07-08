"""
Deprecated ad-hoc database patch script.

Use SQL migrations under sql/migrations/ instead. Ad-hoc scripts with hardcoded
credentials are intentionally disabled.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("patch_db.py is disabled: use reviewed SQL migrations instead.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

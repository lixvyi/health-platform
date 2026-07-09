"""
Deprecated board attribution fixer.

The old script directly modified CMS rows using hardcoded credentials. Category
relations are now managed through knowledge_category and content_category_rel.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("fix_board_attribution.py is disabled: use category relation migrations/importers instead.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

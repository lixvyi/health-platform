"""
Deprecated destructive rebuild script.

The old implementation deleted CMS rows and re-imported a mixture of crawl and
seed data. It is intentionally disabled to protect the current database.

Use migration V002 plus scripts/health_resource_import.py for audited, idempotent
imports. Do not delete existing CMS content without an explicit backup and user
confirmation.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("rebuild_encyclopedia_from_real_data.py is disabled: destructive CMS rebuilds are not allowed.")
    print("Use audited migrations/importers instead.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

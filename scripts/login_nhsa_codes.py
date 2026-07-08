"""
Deprecated placeholder for the NHSA medical terminology crawler.

This script is intentionally disabled.

Reasons:
1. The target site requires authenticated access.
2. The old version contained plaintext personal credentials.
3. Health-platform imports must be source-traceable and must not scrape behind
   login walls without explicit authorization and a reviewed compliance plan.

If this capability is needed again, create a new script that:
- reads credentials only from environment variables;
- performs a dry-run first;
- writes raw exports to data/external-import/ or data/processed/;
- records the import in data_resource_dataset / data_resource_import_run;
- never fabricates or guesses medical terminology data.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("login_nhsa_codes.py is disabled for safety.")
    print("Use manually exported official data or build a reviewed env-var based importer instead.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

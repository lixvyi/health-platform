"""
Deprecated ICD-10 sample importer.

The previous script could create sample ICD-10 records when no official source
file was present. That conflicts with the current project rule: do not fabricate
medical data.

For ICD-10 / medical terminology, use manually exported official files or a
reviewed importer that records source_file/source_sheet/source_row metadata.
"""

from __future__ import annotations

import sys


def main() -> int:
    print("import_icd10.py is disabled: sample ICD-10 generation is not allowed.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

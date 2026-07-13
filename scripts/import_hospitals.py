"""Compatibility entry point for importing only the national hospital directory."""

from __future__ import annotations

import sys

from import_medical_resources import main


if __name__ == "__main__":
    sys.exit(main(["--datasets", "hospitals", *sys.argv[1:]]))

#!/usr/bin/env python3
"""
VSB Configuration Utilities for DSpace 7
Shared configuration loading functions for VSB scripts
"""

from pathlib import Path


def load_vsb_config():
    """Return vsb.base.url and vsb.test.url from config/dspace.cfg."""
    # --- Locate the config file ---
    current = Path(__file__).resolve().parent
    while current != current.parent:
        cfg = current / "config" / "dspace.cfg"
        if cfg.exists():
            break
        current = current.parent
    else:
        raise FileNotFoundError("Could not find config/dspace.cfg")

    # --- Read the two URLs ---
    vsb_base, vsb_test = None, None
    with cfg.open(encoding="utf-8") as f:
        for line in f:
            if line.strip().startswith("vsb.base.url"):
                vsb_base = line.split("=", 1)[1].strip()
            elif line.strip().startswith("vsb.test.url"):
                vsb_test = line.split("=", 1)[1].strip()

    if not (vsb_base and vsb_test):
        raise ValueError("Missing vsb.base.url or vsb.test.url in dspace.cfg")
    return vsb_base, vsb_test


# Load configuration once at module level for convenience
try:
    VSB_BASE_URL, VSB_TEST_URL = load_vsb_config()
except (FileNotFoundError, ValueError) as e:
    # Allow module to be imported even if config is not available
    # Scripts can handle this gracefully if needed
    VSB_BASE_URL, VSB_TEST_URL = None, None
    print(f"Warning: Could not load VSB configuration: {e}")
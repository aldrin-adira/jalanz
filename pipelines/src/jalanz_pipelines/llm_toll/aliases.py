"""Plaza name normalisation for alias matching (04 §4 step G).

The portal spells names inconsistently, e.g. "IPOH(S)" next to
"BUKIT TAMBUN (U)". Aliases are stored per source and portal highway, keyed
by the normalised name, and point at our stable plaza_id (04 §3).
"""

from __future__ import annotations

import re
import unicodedata

_PREFIXES = ("PLAZA TOL ", "TOL PLAZA ", "PLAZA ")


def normalise(name: str) -> str:
    s = unicodedata.normalize("NFKC", name).upper()
    s = re.sub(r"[‐-―]", "-", s)
    s = re.sub(r"\s*\(\s*", " (", s)
    s = re.sub(r"\s*\)", ")", s)
    s = re.sub(r"\s*-\s*", "-", s)
    s = " ".join(s.split())
    for p in _PREFIXES:
        if s.startswith(p):
            s = s[len(p):]
            break
    return s

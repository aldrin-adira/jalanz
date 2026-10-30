"""Crawler for the official LLM toll calculator (spec 04 §4, 05 §3).

The calculator that spec 05 calls kadartol.llm.gov.my is served today as a
small JSON API on www.llm.gov.my (see pipelines/README.md for the findings).
"""

import datetime as _dt

SOURCE_ID = "llm_kadartol"
MYT = _dt.timezone(_dt.timedelta(hours=8), "Asia/Kuala_Lumpur")

"""Read board observations from both private capture archive formats.

One v2 capture owns one image and several independently labelled boards.
Group IDs always describe a board; capture IDs always describe a photograph.
"""

from __future__ import annotations

import re

UUID = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\Z")


def observation_id(record: dict) -> str:
    capture_id = record["capture_id"]
    board_id = record.get("board_id")
    return f"{capture_id}:{board_id}" if board_id else capture_id


def board_records(capture: dict, *, labeled_only: bool = True) -> list[dict]:
    """Flatten observations without losing their shared source-image identity."""
    version = capture.get("schema_version", 1)
    if version not in (1, 2):
        raise ValueError(f"Unsupported archive schema: {version}")
    if not UUID.fullmatch(capture["capture_id"]):
        raise ValueError("Invalid capture ID")
    if capture["image_file"] != f"images/{capture['capture_id']}.jpg":
        raise ValueError("Image filename differs from capture ID")
    if version == 1:
        records = [dict(capture)]
    else:
        boards = capture.get("boards")
        if not isinstance(boards, list):
            raise ValueError("Version 2 requires a boards array")
        ids = [board.get("board_id", "") for board in boards]
        if len(set(ids)) != len(ids) or any(not UUID.fullmatch(value) for value in ids):
            raise ValueError("Invalid or duplicate board ID")
        records = []
        for board in boards:
            record = {**capture, **board}
            # The source image belongs to the capture, never to an observation.
            for key in ("capture_id", "image_file", "image_width", "image_height", "image_sha256", "schema_version"):
                record[key] = capture[key]
            record.pop("boards", None)
            records.append(record)
    for record in records:
        if not UUID.fullmatch(record.get("group_id", "")):
            raise ValueError("Invalid board group ID")
        if not isinstance(record.get("shot_index"), int) or record["shot_index"] < 1:
            raise ValueError("Invalid shot index")
        record["observation_id"] = observation_id(record)
    return [record for record in records if not labeled_only or record.get("status") == "labeled"]

import copy
import unittest
import uuid

from archive_records import board_records


class ArchiveRecordsTest(unittest.TestCase):
    def capture(self, version=1):
        capture_id = str(uuid.uuid4())
        return {"schema_version": version, "capture_id": capture_id,
                "image_file": f"images/{capture_id}.jpg", "image_width": 1000,
                "image_height": 1200, "image_sha256": "abc", "status": "unlabeled"}

    def board(self, status="labeled"):
        return {"board_id": str(uuid.uuid4()), "group_id": str(uuid.uuid4()),
                "shot_index": 1, "status": status, "labels": ["FOX"] * 20}

    def test_reads_legacy_record_and_skips_unconfirmed_photos(self):
        capture = {**self.capture(), **self.board()}
        capture.pop("board_id")
        self.assertEqual(capture["capture_id"], board_records(capture)[0]["observation_id"])
        capture["status"] = "unlabeled"
        self.assertEqual([], board_records(capture))

    def test_shared_photo_has_independent_observations_and_groups(self):
        capture = {**self.capture(2), "boards": [self.board(), self.board(), self.board("unlabeled")]}
        original = copy.deepcopy(capture)
        records = board_records(capture)
        self.assertEqual(2, len(records))
        self.assertEqual(1, len({record["capture_id"] for record in records}))
        self.assertEqual(2, len({record["observation_id"] for record in records}))
        self.assertEqual(2, len({record["group_id"] for record in records}))
        self.assertEqual(original, capture)
        self.assertEqual(3, len(board_records(capture, labeled_only=False)))

    def test_rejects_duplicate_boards_unknown_version_and_source_override(self):
        board = self.board()
        with self.assertRaises(ValueError):
            board_records({**self.capture(2), "boards": [board, board]})
        with self.assertRaises(ValueError):
            board_records(self.capture(3))
        capture = {**self.capture(2), "boards": [{**board, "image_file": "images/other.jpg"}]}
        self.assertEqual(capture["image_file"], board_records(capture)[0]["image_file"])


if __name__ == "__main__":
    unittest.main()

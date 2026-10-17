import csv
import tempfile
import unittest
from pathlib import Path
from summarize_stats import summarize


class SummaryTest(unittest.TestCase):
    def test_aborts_and_duplicate_selection_do_not_inflate_win_rate(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "events.csv"
            fields = ["round", "mode", "map", "profile", "actor", "class", "faction", "event", "value", "detail"]
            with path.open("w", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=fields)
                writer.writeheader()
                base = dict(mode="CORE", map="template", profile="FILM", actor="1", **{"class": "runner"}, faction="GLADERS", value="1")
                writer.writerow(dict(base, round="one", event="class_selected", detail=""))
                writer.writerow(dict(base, round="one", event="class_selected", detail=""))
                writer.writerow(dict(base, round="one", event="match_result", detail="ESCAPED"))
                writer.writerow(dict(base, round="two", event="class_selected", detail=""))
                writer.writerow(dict(base, round="two", event="match_result", detail="ADMIN_STOP"))
            report = summarize(path)
            self.assertEqual(report["groups"][0]["appearances"], 1)
            self.assertEqual(report["groups"][0]["win_rate"], 1)
            self.assertEqual(report["groups"][0]["interpretation"], "insufficient sample")


if __name__ == "__main__":
    unittest.main()

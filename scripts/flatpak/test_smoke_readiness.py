"""Execute the real shell readiness block against delayed, blank and exited app captures."""
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipIf(os.name == "nt" or not shutil.which("bash"), "Requires native POSIX Bash (the Flatpak CI host)")
class PaintedWindowTest(unittest.TestCase):
    def run_readiness(self, ready_after=None, app_alive=True):
        source = Path(__file__).with_name("smoke.sh").read_text()
        block = "painted=false\n" + source.split("painted=false\n", 1)[1].split("\ngdbus call", 1)[0]
        with tempfile.TemporaryDirectory() as directory:
            prelude = f"""set -euo pipefail
work={shlex.quote(directory)}
window=42
app_pid=99
import() {{ printf captured > "${{@: -1}}"; }}
identify() {{
  count=$(cat "$work/count" 2>/dev/null || printf 0)
  count=$((count+1))
  printf '%s' "$count" > "$work/count"
  if ((count >= {ready_after or 1000})); then printf 450; else printf 1; fi
}}
kill() {{ return {0 if app_alive else 1}; }}
sleep() {{ :; }}
"""
            result = subprocess.run(["bash", "-c", prelude + block], capture_output=True, text=True, timeout=15)
            path = Path(directory)
            evidence = (path / "paint-readiness.txt").read_text()
            return result, evidence, (path / "application-first.png").exists()

    def test_delayed_paint_is_required_and_initial_blank_is_retained(self):
        result, evidence, first = self.run_readiness(ready_after=3)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(3, len(evidence.splitlines()))
        self.assertIn("colors=1", evidence)
        self.assertIn("colors=450", evidence)
        self.assertTrue(first)

    def test_blank_surface_never_passes_and_has_a_bounded_capture_count(self):
        result, evidence, _ = self.run_readiness()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(120, len(evidence.splitlines()))
        self.assertIn("did not paint", result.stderr)

    def test_exited_application_fails_before_waiting_for_more_frames(self):
        result, evidence, _ = self.run_readiness(app_alive=False)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(evidence.splitlines()))


if __name__ == "__main__":
    unittest.main()

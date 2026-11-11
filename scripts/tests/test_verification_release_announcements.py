import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "send-release-announcement.py"
spec = importlib.util.spec_from_file_location("release_announcements", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
URL = "https://github.com/goosepod/naviamp/releases/tag/v2.11.1"


class ReleaseAnnouncementTest(unittest.TestCase):
    def test_actual_release_copy_uses_one_message_and_preserves_bullets(self):
        notes = SCRIPT.parents[1] / ".github/releases/v2.11.1-announcement.md"
        payload = module.announcement_payload(notes.read_text(), URL)
        self.assertEqual([], payload["allowed_mentions"]["parse"])
        self.assertEqual(1, len(payload["embeds"]))
        message = payload["embeds"][0]
        self.assertIn("- Inspect", message["description"])
        self.assertIn("Stream Details", message["description"])
        self.assertLessEqual(len(message["description"]), 4096)
        self.assertEqual(URL, message["url"])

    def test_preserves_community_thanks_without_discord_pings(self):
        payload = module.announcement_payload("Naviamp v2.11.1\n\n- New feature.\n\nThanks\n\n- Thanks @contributor for testing.", URL)
        self.assertIn("@contributor", payload["embeds"][0]["description"])
        self.assertEqual([], payload["allowed_mentions"]["parse"])

    def test_rejects_mismatched_version_detailed_notes_and_oversized_copy(self):
        for notes in ("Naviamp v2.11.0\n- A change", "Naviamp v2.11.1: subtitle\n- Change",
                      "Naviamp v2.11.1\n- See https://github.com/goosepod/naviamp/issues/586",
                      "Naviamp v2.11.1\n" + "a" * 4097, "Naviamp v2.11.1\n"):
            with self.subTest(notes=notes[:60]), self.assertRaises(ValueError):
                module.announcement_payload(notes, URL)

    def test_reuses_the_existing_webhook_without_github_payload_adapter(self):
        expected = "https://discord.com/api/webhooks/123/fake_token?wait=true"
        self.assertEqual(expected, module.discord_endpoint("https://discord.com/api/webhooks/123/fake_token/github"))
        self.assertEqual(expected, module.discord_endpoint("https://discord.com/api/webhooks/123/fake_token"))
        for value in ("http://discord.com/api/webhooks/123/fake", "https://example.com/api/webhooks/123/fake", "https://discord.com/api/webhooks/123/fake?extra=true"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                module.discord_endpoint(value)

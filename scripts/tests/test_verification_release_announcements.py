import importlib.util
import http.client
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

SCRIPT = Path(__file__).resolve().parents[1] / "send-release-announcement.py"
spec = importlib.util.spec_from_file_location("release_announcements", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
URL = "https://github.com/goosepod/naviamp/releases/tag/v2.11.1"
FAKE_WEBHOOK = "https://discord.com/api/webhooks/123/fake_token"


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

    def test_delivery_sends_one_identified_post_and_reports_its_receipt(self):
        payload = module.announcement_payload("Naviamp v2.11.1\n- A fix.", URL)
        with patch.object(module.urllib.request, "urlopen", return_value=io.BytesIO(b'{"id":"12345"}')) as request:
            with patch("builtins.print") as report:
                module.send_announcement(FAKE_WEBHOOK, payload)

        request.assert_called_once()
        sent = request.call_args.args[0]
        self.assertEqual("POST", sent.get_method())
        self.assertEqual(FAKE_WEBHOOK + "?wait=true", sent.full_url)
        self.assertEqual("DiscordBot (https://github.com/goosepod/naviamp, 1.0)", sent.get_header("User-agent"))
        self.assertEqual("application/json", sent.get_header("Content-type"))
        self.assertEqual(payload, json.loads(sent.data))
        report.assert_called_once_with("Delivered release announcement; Discord message ID 12345")

    def test_webhook_check_uses_get_only_and_verifies_the_channel(self):
        responses = (b'{"channel_id":"1547608244099489852"}', b'{"channel_id":"999"}')
        for response in responses:
            with self.subTest(response=response):
                with patch.object(module.urllib.request, "urlopen", return_value=io.BytesIO(response)) as request:
                    if response == responses[0]:
                        with patch("builtins.print"):
                            module.check_webhook(FAKE_WEBHOOK)
                    else:
                        with self.assertRaisesRegex(SystemExit, "did not confirm"):
                            module.check_webhook(FAKE_WEBHOOK)
                request.assert_called_once()
                sent = request.call_args.args[0]
                self.assertEqual("GET", sent.get_method())
                self.assertEqual(FAKE_WEBHOOK, sent.full_url)
                self.assertIsNone(sent.data)
                self.assertEqual(module.DISCORD_USER_AGENT, sent.get_header("User-agent"))

    def test_delivery_errors_are_classified_without_details_or_retries(self):
        cases = (
            (urllib.error.HTTPError(FAKE_WEBHOOK, 403, FAKE_WEBHOOK, {}, None), "HTTP 403"),
            (urllib.error.URLError(FAKE_WEBHOOK), "could not connect"),
            (urllib.error.URLError(TimeoutError(FAKE_WEBHOOK)), "timed out"),
            (TimeoutError(FAKE_WEBHOOK), "timed out"),
            (http.client.RemoteDisconnected(FAKE_WEBHOOK), "connection failed"),
        )
        for error, diagnosis in cases:
            with self.subTest(diagnosis=diagnosis):
                with patch.object(module.urllib.request, "urlopen", side_effect=error) as request:
                    with self.assertRaisesRegex(SystemExit, diagnosis) as failure:
                        module.send_announcement(FAKE_WEBHOOK, {})
                    request.assert_called_once()
                self.assertNotIn("fake_token", str(failure.exception))
                self.assertTrue(failure.exception.__suppress_context__)

    def test_delivery_rejects_invalid_json_and_receipts_without_exposing_their_values(self):
        for response, diagnosis in (
            (b'<html>fake_token</html>', "invalid JSON"),
            (b'[]', "no valid message receipt"),
            (b'{"id":"fake_token"}', "no valid message receipt"),
        ):
            with self.subTest(diagnosis=diagnosis):
                with patch.object(module.urllib.request, "urlopen", return_value=io.BytesIO(response)) as request:
                    with self.assertRaisesRegex(SystemExit, diagnosis) as failure:
                        module.send_announcement(FAKE_WEBHOOK, {})
                    request.assert_called_once()
                self.assertNotIn("fake_token", str(failure.exception))

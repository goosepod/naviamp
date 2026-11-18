#!/usr/bin/env python3
"""Send the versioned plain-list release announcement through the existing Discord channel."""
import argparse
import http.client
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request


DISCORD_USER_AGENT = "DiscordBot (https://github.com/goosepod/naviamp, 1.0)"
ANNOUNCEMENTS_CHANNEL_ID = "1547608244099489852"


def announcement_payload(notes, release_url):
    title, separator, body = notes.strip().partition("\n")
    if not re.fullmatch(r"Naviamp v\d+\.\d+\.\d+", title):
        raise ValueError("Announcement title must be exactly Naviamp vX.Y.Z")
    if not separator or not body.strip():
        raise ValueError("Announcement body is empty")
    tag = title.removeprefix("Naviamp ")
    expected_url = f"https://github.com/goosepod/naviamp/releases/tag/{tag}"
    if release_url != expected_url:
        raise ValueError("Announcement version does not match the release URL")
    if re.search(r"https://github.com/[^\s)]+/(?:issues|pull|commit)/", body):
        raise ValueError("Discord copy must use plain changes without per-change GitHub references")
    body = body.strip()
    if len(body) > 4096:
        raise ValueError("Plain-list announcement exceeds Discord's 4096-character description limit")
    return {"allowed_mentions": {"parse": []},
            "embeds": [{"title": title, "url": release_url, "description": body}]}


def discord_endpoint(value, *, wait_for_receipt=True):
    url = urllib.parse.urlsplit(value.strip())
    if url.scheme != "https" or url.hostname != "discord.com" or url.query or url.fragment:
        raise ValueError("Expected an HTTPS discord.com webhook URL without query or fragment")
    path = url.path.removesuffix("/github")
    if not re.fullmatch(r"/api/webhooks/\d+/[A-Za-z0-9_-]+", path):
        raise ValueError("Expected a Discord webhook endpoint")
    query = "wait=true" if wait_for_receipt else ""
    return urllib.parse.urlunsplit(("https", "discord.com", path, query, ""))


def discord_request(endpoint, operation, payload=None):
    headers = {"User-Agent": DISCORD_USER_AGENT}
    data = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    request = urllib.request.Request(endpoint, data=data, headers=headers)
    # Exceptions and response bodies can contain webhook credentials. Report only safe categories.
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        error.close()
        raise SystemExit(f"Discord {operation} returned HTTP {error.code}.") from None
    except urllib.error.URLError as error:
        cause = "timed out" if isinstance(error.reason, TimeoutError) else "could not connect"
        raise SystemExit(f"Discord {operation} {cause}.") from None
    except TimeoutError:
        raise SystemExit(f"Discord {operation} timed out.") from None
    except (OSError, http.client.HTTPException):
        raise SystemExit(f"Discord {operation} connection failed.") from None
    except ValueError:
        raise SystemExit(f"Discord {operation} returned invalid JSON.") from None
    return result


def check_webhook(webhook):
    endpoint = discord_endpoint(webhook, wait_for_receipt=False)
    result = discord_request(endpoint, "webhook check")
    if not isinstance(result, dict) or result.get("channel_id") != ANNOUNCEMENTS_CHANNEL_ID:
        raise SystemExit("Discord webhook check did not confirm the Naviamp announcements channel.")
    print("Discord webhook matches Naviamp #announcements (read-only check).")


def send_announcement(webhook, payload):
    endpoint = discord_endpoint(webhook)
    # One POST: an error or a lost receipt does not prove that the message was not posted.
    result = discord_request(endpoint, "announcement delivery", payload)
    message_id = result.get("id") if isinstance(result, dict) else None
    if not isinstance(message_id, str) or not re.fullmatch(r"[0-9]{1,20}", message_id):
        raise SystemExit("Discord returned no valid message receipt; inspect the channel before retrying.")
    print(f"Delivered release announcement; Discord message ID {message_id}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes", type=Path)
    parser.add_argument("--release-url")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--check-webhook", action="store_true", help="Verify the destination with a read-only request.")
    args = parser.parse_args()
    if args.check_webhook:
        if args.notes or args.release_url or args.dry_run:
            parser.error("--check-webhook is separate from release delivery and dry runs")
        check_webhook(os.environ["NAVIAMP_RELEASE_DISCORD_WEBHOOK"])
        return
    if not args.notes or not args.release_url:
        parser.error("--notes and --release-url are required for release delivery or dry runs")
    payload = announcement_payload(args.notes.read_text(), args.release_url)
    if args.dry_run:
        print(json.dumps(payload, indent=2))
        return
    send_announcement(os.environ["NAVIAMP_RELEASE_DISCORD_WEBHOOK"], payload)


if __name__ == "__main__":
    main()

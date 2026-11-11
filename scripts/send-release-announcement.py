#!/usr/bin/env python3
"""Send the versioned plain-list release announcement through the existing Discord channel."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.parse
import urllib.request


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


def discord_endpoint(value):
    url = urllib.parse.urlsplit(value.strip())
    if url.scheme != "https" or url.hostname != "discord.com" or url.query or url.fragment:
        raise ValueError("Expected an HTTPS discord.com webhook URL without query or fragment")
    path = url.path.removesuffix("/github")
    if not re.fullmatch(r"/api/webhooks/\d+/[A-Za-z0-9_-]+", path):
        raise ValueError("Expected a Discord webhook endpoint")
    return urllib.parse.urlunsplit(("https", "discord.com", path, "wait=true", ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes", required=True, type=Path)
    parser.add_argument("--release-url", required=True)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    payload = announcement_payload(args.notes.read_text(), args.release_url)
    if args.dry_run:
        print(json.dumps(payload, indent=2))
        return
    endpoint = discord_endpoint(os.environ["NAVIAMP_RELEASE_DISCORD_WEBHOOK"])
    request = urllib.request.Request(endpoint, data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
    # One request, without automatic retries: a lost response may already have posted the message.
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
    except Exception:
        # urllib errors may contain the secret webhook URL. Keep failure output credential-free.
        raise SystemExit("Discord delivery failed or is uncertain; inspect the channel before retrying") from None
    if not result.get("id"):
        raise SystemExit("Discord returned no message receipt; inspect the channel before retrying")
    print(f"Delivered release announcement; Discord message ID {result['id']}")


if __name__ == "__main__":
    main()

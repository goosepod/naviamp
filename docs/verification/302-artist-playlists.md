# Artist playlists: manual acceptance

Release scope: v2.10.0, issue [#302](https://github.com/goosepod/naviamp/issues/302).
Owner manual testing is pending. Do not tag or publish a release on the strength of automated tests alone.

1. Connect to a Navidrome/OpenSubsonic account with a regular playlist and a smart playlist you created. Include a track by the artist in each. Open that artist and find **Playlists** below the discography. Both playlists should appear once, in name order. Open each and check its actual contents.
2. Include a track on which the artist is a secondary credited performer. Check that its playlist appears. A different artist with the same name and a different provider identity must not qualify. When the server only supplies names, matching uses an exact name, ignoring case and surrounding spaces.
3. Check playlists created by other users, including one shared with you for editing. They must not appear. A playlist you created that the server reports as read-only should still appear if it contains the artist. Automatically generated Navibeat mixes should remain in their own presentation.
4. Open **Settings → Experience** and turn off **Show playlists on artist pages**. Reopen the artist: the section should be absent and no playlist-content requests should run. Turn it on again and check that results load. Restart the app and verify both choices persist.
5. Export settings with the switch off, change it locally, and import the export. The switch should return to off. Verify settings sync on another platform. An older export without this preference should enable the section.
6. Open a matching playlist and use **Back**: return to the same artist with refreshed results. If you reached this artist through a similar artist, Back again should return to that previous artist. Add/remove tracks on the server, then refresh or reopen the artist and check the updated membership. Rename or delete a playlist and refresh again.
7. Simulate a failing playlist read. The section should explain that some playlists could not be checked, retain successful matches, and offer **Retry**. Recover the connection and retry. An empty result should be shown only after a complete successful lookup. The first 500 listed playlists are checked; larger collections show an explicit warning.
8. Open another artist, switch servers/accounts, or turn the switch off while a lookup is pending. Old results must not appear on the new page or source. A slow playlist request must not delay the artist’s discography.
9. Repeat membership, ownership, refresh, and navigation checks with Jellyfin. Ownership is checked through its creator-only playlist Users endpoint; unavailable or older server endpoints must produce an incomplete-lookup message rather than claiming no matches.
10. Check Desktop mouse/keyboard, a narrow Android screen, iOS touch, and TV navigation where available. Check translated labels, scrolling, readable errors, and playlist artwork. Existing artist portraits must remain circular and album covers square.

Record the tested build/commit, platform, server version, pass/fail results, and any screenshots in the issue and the v2.10.0 acceptance tracker. Performance optimization remains outside this feature; there are no continuous-animation changes.

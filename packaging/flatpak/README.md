# Official Flatpak distribution

The first stable package reuses the public v2.10.0 Linux image, including its Java runtime and native
libraries. `prepare.py` requires the canonical published release's exact asset URL and SHA-256 digest.
It refuses draft/prerelease versions and ambiguous assets. This is a self-hosted binary distribution,
not a Flathub source-build submission. Application source is available at the corresponding public tag.

The supported runtime is Freedesktop 25.08. Review its support window before upgrading it. Only x86_64
is published. Java/Compose uses X11, including XWayland on Wayland desktops; native Wayland is not claimed.

## Build and publish

Publication lives in the dedicated [naviamp-flatpak repository](https://github.com/goosepod/naviamp-flatpak).
Build and sign locally, then upload only the signed public files to a draft distribution release.
The publication workflow checks out accepted Naviamp `main`, verifies the signature against the
pinned public key, tests the package, and deploys GitHub Pages before publishing the release. The
application version stays identical to the source release. Packaging revisions use separate
distribution tags, so no application release tag or existing application binary is rewritten.

The stable remote is `https://goosepod.com/naviamp-flatpak/repo/`. Installers are
`https://goosepod.com/naviamp-flatpak/Naviamp.flatpakref` and `naviamp.flatpakrepo`. The repository's
`repository.json` and `naviamp.asc` pin its public signing identity. Never use `--no-gpg-verify`.

The private key stays on the owner's computer; GitHub receives only signed public files. No private key
belongs in Actions secrets, source, build artifacts, logs, the website, or an application release. The owner's local
signing home is in Ubuntu at `~/.local/share/naviamp-flatpak-signing`, mode 0700. Keep an encrypted,
offline backup of the secret key and revocation certificate. Import
the same identity for later releases; losing it breaks the existing remote's trust continuity.
Plan key expiry/rotation before the recorded expiry, with a signed transition for existing clients.

To prepare a release on Linux, install Flatpak/flatpak-builder, GnuPG, AppStream and zstd, then install
the Freedesktop Platform and SDK 25.08 from Flathub. Fetch the canonical release JSON with
`gh api repos/goosepod/naviamp/releases/tags/vX.Y.Z`. Run:

```sh
python3 scripts/flatpak/prepare.py --release-json release.json --output build/flatpak-input
flatpak-builder --user --force-clean --repo=build/flatpak-repo \
  build/flatpak-app build/flatpak-input/io.github.goosepod.Naviamp.json
python3 scripts/flatpak/publish.py --repo build/flatpak-repo --release-json release.json \
  --signing /path/to/private/signing-home --public build/flatpak-public --assets build/flatpak-assets
```

The publication script only prepares output. It never deploys it. Output directories must be fresh
and separate from signing material. Retain `source.json`, the package digest and acceptance results.
The public repository retains two parent revisions and static deltas; the immutable release archive
backs up its objects for the next publication. The Pages output must remain below 900 MiB, leaving
headroom under GitHub's 1 GiB site limit. Monitor bandwidth as adoption grows. Upload the generated
assets to a draft in `goosepod/naviamp-flatpak` tagged `vX.Y.Z-flatpak.N`, then dispatch its
`Publish Flatpak` workflow with that distribution tag. Increase N for a packaging correction;
never rewrite an existing application release or published distribution tag.

## Sandbox and acceptance

Network supports remote libraries and LAN discovery. X11/IPC and DRI support the existing renderer;
PulseAudio supports native playback. Secret Service and the existing scoped MPRIS name are the only
extra session-bus permissions. Downloads is the only host filesystem permission. The hardcoded
application data path is persisted inside Flatpak's private namespace; settings use its XDG config
directory. Existing non-Flatpak data is neither imported nor modified. Use shared settings export/import
for a deliberate migration, and select accessible download storage.

The runtime supplies the portal-backed desktop tools. The manifest builds the existing `secret-tool`
effect from checksum-pinned libsecret source; no credential policy is duplicated. Linux's in-app
update action only opens release information; Flatpak owns installation and updates to read-only `/app`.
Close and reopen the app after updating. Software-center update scheduling follows the user's settings.

Before publication, install from a signature-verified remote, confirm actual readable UI, native audio
clock, credentials across restart, menus/input, file selection, MPRIS, and persistent settings/database.
Test a real older application version to the new one and reject an untrusted signing key. Check the
deployed HTTPS remote with the exact public installation command before advertising availability.
Keep known Linux performance/accessibility investigations explicit; this packaging is not a performance
fix. Record desktop/runtime versions and any untested hardware/desktop combinations honestly.

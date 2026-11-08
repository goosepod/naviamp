# macOS local-network packaging

Tracking: [#361](https://github.com/goosepod/naviamp/issues/361).

macOS local-network privacy is separate from certificate validation. A successful Terminal-launched
HTTP test does not establish that a packaged application has local-network permission. Apple documents
that unsigned/ad-hoc code and multiple installed copies can make identity tracking unreliable; an
Apple-issued signing identity is recommended for reliable tracking. See
[TN3179](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy).

The packaging configuration was missing its local-network usage description and Bonjour declarations.
`scripts/configure-macos-local-network.py` now reads the usage text from the shared UI string catalogs
and service types from the shared Naviamp Connect and Cast owners. It generates the default
`NSLocalNetworkUsageDescription`, `NSBonjourServices`, all 17 localized `InfoPlist.strings` files and
the corresponding `CFBundleLocalizations`, preserving bundle identity, version and unrelated keys.

Local and release app-image tasks configure and validate these resources before sealing. The existing
desktop distributable validation also rejects missing/outdated messages, localizations or service
types. Catalog and service-source files are declared packaging inputs. The fixture tests are included
in the existing `test_verification_*.py` CI discovery and cover preservation, idempotence, native locale
names/escaping and failure on missing translations or declarations.

Default signing remains ad-hoc for existing local/CI workflows. Local development can opt into an
already-installed signing identity without exporting its key:

```sh
./gradlew :apps:desktop:stageLocalTestApp \
  -Pnaviamp.bass.platform=macos-arm64 \
  -Pcompose.desktop.packaging.checkJdkVendor=false \
  -Pnaviamp.macos.signingIdentity=YOUR_INSTALLED_SIGNING_IDENTITY
```

Use an Apple Development identity for local testing. This option does not publish an app, notarize
it, change existing privacy preferences or replace the release/store signing workflow. Changing the
identity is a declared packaging input, so it cannot silently reuse an app signed with another one.

## Verification and limits

The four new packaging fixtures pass, together with the three existing verification-scope cases.
A full clean-worktree local Mac package build passed all 67 executed tasks, generated all 17 native
localizations, validated its metadata, retained the version/build and passed the existing native-input,
architecture and code-signature checks. Foundation's `plutil` also accepted all localized `.strings`
files in a separately prepared corrected test artifact. That copy was signed with an existing Apple
Development identity and its deep/strict signature verification passed. The new Gradle signing
option was then exercised directly: the development-signed build passed in 24 seconds (22 executed,
45 up-to-date tasks), including final metadata and signature validation.

The corrected test copy is separate from the running app and retains its existing isolated test
profile. No privacy databases, network routes, certificate trust settings or other app permissions
were changed. The owner confirmed that the corrected foreground packaged test app connected
successfully. This verifies the reported connection case; it does not establish acceptance for every
macOS version or permission state. Adding declarations is not a permission grant, nor does it reset a denial.
The earlier test copy's ad-hoc signature cannot be treated as equivalent to the installed app's
Apple-issued identity.

Reproduce metadata fixtures and inspect any built app:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_verification_*.py' -v
python3 scripts/configure-macos-local-network.py --app /path/to/Naviamp.app --check
codesign --verify --deep --strict /path/to/Naviamp.app
```

## Placement

No Android, Desktop/JVM or iOS production Kotlin source changes. The new portable user-facing message
is owned by common resources. The build script and Python helper perform only macOS bundle metadata,
localization and signing operations; the concrete native boundaries are `Info.plist`/`InfoPlist.strings`,
Bonjour declarations and `codesign`. They reuse shared service definitions and contain no product
navigation, provider protocol, retry, scheduling or persistence behavior.

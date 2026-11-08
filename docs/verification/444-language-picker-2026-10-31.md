# Bundled language selection

Tracking: [#444](https://github.com/goosepod/naviamp/issues/444). Final Windows walkthrough
found that Settings offered only English and Spanish despite shipping 17 translation catalogs.
This is a v2.10.0 / build 60 stabilization fix; it creates no release or tag.

## Scope and compatibility

- The shared interface-language enum now exposes every maintained catalog plus System default.
  Existing enum names and order for System, English and Spanish are preserved.
- Both standard and TV Settings already enumerate the shared model. New native language names
  and the selection subtitle are resources defined in all 17 maintained catalogs.
- Regional choices use `pt-BR` and `zh-CN`. Indonesian uses modern `id`; resource packaging generates
  an identical legacy `in` alias from the single maintained `values-id` catalog because native
  locale APIs can expose either code. No duplicated translation source is maintained.
- Common locale presentation sets Arabic to right-to-left and other explicit choices to
  left-to-right, restoring the host's ambient direction for System default. Existing native
  locale adapters remain unchanged. No Android, Desktop or iOS production source is changed.
- Partial catalogs still fall back to English for missing strings. This does not claim complete
  translations. Older exports retain their defaults and existing identities; selecting a new
  enum value requires v2.10.0 or later on other devices receiving settings sync.

## Verification

Focused tests cover catalog/model parity, resource keys in every catalog, scrolling and selecting
all 18 actual Settings choices, actual resource resolution for all 17 explicit languages,
preserved composition identity and keyboard focus, Arabic direction and System restoration,
settings export/import and normalization, old defaults and unknown-value validation, and the
updated shared About changelog and its rendered scrolling presentation.

Windows focused run: **27 JVM tests passed**, no failures, errors or skips. Android
`compileDebugAndroidTestKotlinAndroid` passed. The four macOS local-network packaging helper
tests passed with Python UTF-8 mode enabled (the initial Windows default-code-page invocation
could not encode the existing non-Latin test fixture). Both generated Indonesian catalog files
have SHA-256 `60d65aecb67105a55fb729ab4e9ccee9e0daef0c7eb2201ded7aa97bfe9eb4e4`.

The actual resource-resolution test is shared by JVM and Android rendered-test source sets and
registered in both Android device-matrix runs. The existing iOS native locale test now exercises
every explicit language tag and restores the captured native preference afterward. Windows cannot
execute iOS tests; hosted cross-platform results and exact source provenance are recorded on the
linked pull requests and issue. Android compilation alone is not claimed as device execution.

The macOS local-network resource packaging tests retain coverage of legacy `in` input and the
native `id` output. Generated Indonesian alias and canonical catalog bytes are checked identical.
The resource staging uses the supported Compose `customDirectory` API described in the
[resource configuration documentation](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html).

The owner's existing Windows process and profile are left untouched while preparing the fix.
Updated Windows output is staged separately; the earlier release-pass artifacts do not contain
this fix and must not be mistaken for the final accepted release candidate.

# Third-party notices

Naviamp includes third-party software. This file records the notices most relevant to the native
audio components and data shipped with the application. Each component remains under its own
license; the Naviamp GPL license and BASS linking exception do not replace those terms.

## MusicBrainz genre ontology

Naviamp bundles a generated snapshot of MusicBrainz genre entities, aliases, and genre-to-genre
relationships to organize genre tags already present in a user's library. The imported MusicBrainz
core data is made available under CC0 1.0. Naviamp does not bundle MusicBrainz's supplementary
artist, recording, or release tag associations and does not contact MusicBrainz at runtime.

MusicBrainz data license: <https://musicbrainz.org/doc/About/Data_License>

CC0 1.0 Universal: <https://creativecommons.org/publicdomain/zero/1.0/>

## BASS audio library and official add-ons

BASS and its official add-ons are copyright Un4seen Developments Ltd. Naviamp uses them under
Un4seen's free non-commercial terms. Those terms permit free use by a non-commercial entity when
the product makes no money through sales, advertising, or similar means. Commercial use requires
an appropriate BASS license for each platform. BASS is provided without warranty and is used at
the user's own risk.

Current terms and commercial license information: <https://www.un4seen.com/bass.html>

The upstream BASS and add-on notices shipped with the iOS vendor inventory are retained in
[`platforms/ios/vendor/bass/licenses`](platforms/ios/vendor/bass/licenses). Those notices apply to
the corresponding Android and Desktop binaries too.

The upstream packages include these acknowledgements:

- MP3 decoding in BASS is based on minimp3.
- Ogg Vorbis decoding is based on libogg/vorbis, copyright 2002-2020 Xiph.org Foundation.
- BASSFLAC uses libFLAC, copyright 2000-2009 Josh Coalson and copyright 2011-2025 Xiph.Org
  Foundation.
- BASSOPUS uses libOpus, copyright 2001-2011 Xiph.Org, Skype Limited, Octasic, Jean-Marc Valin,
  Timothy B. Terriberry, CSIRO, Gregory Maxwell, Mark Borgerding, and Erik de Castro Lopo.
- BASSWEBM uses the nestegg WebM demuxer, copyright 2010 Mozilla Foundation.
- BASSWV uses the WavPack library, copyright 1998-2024 David Bryant.
- BASSAPE uses the Monkey's Audio SDK, copyright 2000-2025 Matthew T. Ashland.

## BASS_SSL and OpenSSL

BASS_SSL is based on OpenSSL. The distributed OpenSSL 1.1.1 code is covered by the OpenSSL License
and the original SSLeay License.

This product includes software developed by the OpenSSL Project for use in the OpenSSL Toolkit
(<https://www.openssl.org/>). This product includes cryptographic software written by Eric Young
(eay@cryptsoft.com).

OpenSSL 1.1.1 license text: <https://www.openssl.org/source/license-openssl-ssleay.txt>

## BASS_MPC

BASS_MPC is copyright 2002-2012 Sebastian Andersson. Portions are copyright 2006 The Musepack
Development Team. The upstream package permits the library to be used and distributed and is
provided without warranty.

The upstream notice is retained at
[`platforms/ios/vendor/bass/licenses/bass_mpc-readme.txt`](platforms/ios/vendor/bass/licenses/bass_mpc-readme.txt).

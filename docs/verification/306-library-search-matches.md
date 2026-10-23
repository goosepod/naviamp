# Library server-match acceptance

Issue: [#306](https://github.com/goosepod/naviamp/issues/306), v2.10.0.

Library search uses the provider's matching rules for Artists, Albums and Songs.
Results returned for the current query are displayed without an additional literal
substring filter. Cached album browsing remains separate from server query results.

- Search `Rufus` in Search and in Library → Artists. Confirm the server-returned
  `RÜFÜS DU SOL`, collaboration credits and `Rufus Thomas` remain visible in Library.
- Repeat in Albums, including an already-cached album library, and in Songs. Check
  an accented name and a match based on an alias, composer or other server metadata
  absent from the displayed title. Compare the appropriate media category; global
  Search and Library may use different page sizes.
- Change the query quickly. Old-query results must not replace the current query.
- Scroll enough results to load another page. Valid server matches must remain visible
  and pagination must continue without the refresh panel.
- Clear the query. Confirm the previous browse catalog returns without an index refresh.
- Switch Library tabs and return to a completed search. Matches remain intact; switching
  back to an album query must not replace it with the cached unfiltered album catalog.
- Explicitly refresh with a query active, then clear. Confirm the refreshed browse data
  is restored and the search field remains stationary.
- Repeat after an offline/failure response and reconnect; verify the error and retry path.

No new settings, strings, database schema or platform-specific behavior are introduced.
Record exact candidate source and package hash in [#280](https://github.com/goosepod/naviamp/issues/280).
Owner manual acceptance is pending. No release or tag is authorized.

# Genre ontology import audit

MusicBrainz snapshot: `2026-08-17`
Generated payload SHA-256: `8a4e03c4d4b6bb7a17cf0b63b37559568ec7ff59c3751d21d27586ec9e8721b4`

## Summary

- Genres: 2184
- Aliases: 946 (945 MusicBrainz, 1 Naviamp compatibility)
- Relationships: 3485
- Subgenre relationships: 1574
- Fusion relationships: 180
- Influence relationships: 1731
- Roots in the subgenre projection: 618
- Genres isolated from the subgenre projection: 505
- Genres with multiple parents: 8
- Maximum parent count: 2
- Maximum direct subgenre count: 65
- Cycle components: 0
- Genres participating in cycles: 0
- Maximum depth after condensing cycles: 5
- Relationships with unknown endpoints: 0

## Interpretation

The stored ontology is a graph, not a tree. The product UI must choose a deterministic preferred
parent for multiple-parent genres and must protect recursive traversal from cycles. Fusion and
influence edges are retained for future discovery features but are not part of descendant expansion
for Genre Mix playback.

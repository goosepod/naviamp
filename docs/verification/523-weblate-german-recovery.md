# Weblate German synchronization recovery

## Cause and resolution

Hosted Weblate automatically locked Application UI after its rebase failed at
`419cb05aea984bdd9c1c4204bb047f509d598fc3` in the German string catalog.
The exported branch also contains `283c8c6b7`.

Both translations were already accepted by squash PR #507 (`b41bd795e`), but
their original commits were absent from main's ancestry. Merge the exported
Weblate history into current main (`c5e119f2f`) with a regular merge, retaining
both original commits. The merge resolves cleanly with no resource changes.
The recovery PR must also use a regular merge, not squash or rebase.

## Local verification

- The merge index before adding this report is identical to origin/main.
- All 1,207 named entries from Weblate's German catalog match current main,
  comparing full XML elements including plural children and attributes while
  excluding whitespace between entries. Thus placeholders and plural content
  are preserved without modification.
- The current German catalog retains its two newer Aurral strings, for 1,209
  entries total.
- Every maintained values*/strings.xml catalog parses and has unique names.
- No platform production files, settings, schemas or product behavior changed.

## Completion gate

After required PR checks pass, merge with original Weblate ancestry retained.
Verify exported Weblate main synchronizes to accepted upstream, the merge error
clears, and Application UI is unlocked for contributions. Do not reset the
repository or discard translator work.

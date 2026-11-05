# 0002 — Schema constraints that shaped the first migrations

Date: 2026-08-22
Stage: 1 — Schema and persistence
Status: accepted

## Context

Writing the first four Liquibase changesets surfaced three constraints that are invisible
from the PRD's data model and would each have produced a runtime failure rather than a
compile error.

## Decision

**Dedupe uses an expression index on `md5(url)`, not on `url`.** The dedupe rule is "one
live bookmark per (user, url)", implemented as:

```sql
CREATE UNIQUE INDEX ux_bookmarks_user_url ON bookmarks (user_id, md5(url))
WHERE deleted_at IS NULL
```

**`Instant` is pinned to `TIMESTAMP_UTC`.** All timestamp columns are
`TIMESTAMP WITH TIME ZONE`, and `application.properties` sets
`spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP_UTC`.

**Enum columns are `varchar` plus a check constraint**, per the plan, and the entity
`@Column(length = …)` must match the changeset exactly.

## Alternatives

**Indexing `url` directly.** A btree entry has a ~2704-byte page limit. `url` is `TEXT`, so a
long URL — legitimate or hostile — makes the *insert* fail with an index-row-size error, not
the validation layer. Capping the column at `varchar(2048)` was considered and rejected:
2048 UTF-8 characters can still exceed the limit in bytes. Hashing sidesteps the size
question entirely. Collisions are irrelevant at this scale, and a false positive only means
one duplicate save is rejected.

**A separate `url_hash` column.** Equivalent safety, but adds a column the application must
remember to populate and keep in sync. The expression index needs no application awareness at
all.

**Leaving Hibernate's default `Instant` mapping.** Hibernate maps `Instant` to plain
`timestamp` by default, which fails `ddl-auto=validate` against `timestamptz` columns.
Switching the columns to `timestamp` instead would have worked, but throws away timezone
information the V2 digest needs (per-user cron in the user's own zone). Using
`OffsetDateTime` in the entities was the other option — rejected as noise in a domain that
only ever means "an instant".

## Consequences

- `md5()` makes the index Postgres-specific. This is already true of the queue design (D5),
  so it costs nothing new — but it is another reason tests must run against real Postgres,
  never H2. Recorded in `docs/CODING_STANDARDS.md`.
- The partial `WHERE deleted_at IS NULL` means a soft-deleted article can be saved again,
  which is the behaviour we want and is covered by
  `BookmarkRepositoryTest.allowsResavingUrlAfterSoftDelete`.
- `preferred_instant_jdbc_type` is flagged by Hibernate as *incubating* (HHH90006001, logged
  once per entity at startup). It is stable in practice but could change in a future
  Hibernate major. If it is ever removed, the fix is to switch the entity fields to
  `OffsetDateTime`; the columns stay as they are.
- `bookmarks.audio_storage_key` replaces the PRD's `audioFilePath`. Direct consequence of D4
  (audio lives in R2) — there is no filesystem path to store.

## Verification note

`KnowCommuteApplicationTests.contextLoads` is now a real schema test, not a smoke test.
Confirmed by deliberately renaming `Bookmark.sourceDomain`'s column and observing the build
fail with `SchemaManagementException: Schema validation: missing column [source_domain_typo]
in table [bookmarks]`. Any drift between an entity and its changeset fails the build.

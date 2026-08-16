# Manual test procedure

Covers what the 60 JVM tests structurally cannot.

**There is no app to click through.** No UI module, no network code, no sync. So none of this is "tap through the app" — it is the four checks that stand in for the automation that cannot exist yet, in value order.

Total: about 30 minutes, or 8 if you skip §2.

## 1. Schema drift — every time, 30 seconds

The one that runs before every commit touching `:core:database`.

```
git diff --stat core/database/schemas/
```

**Pass:** no output, or a **new** `2.json` alongside an untouched `1.json`. Confirmed 2026-08-08 that a version bump produces exactly that shape.

**Fail:** `1.json` shows as modified. That means an entity changed shape without a version bump. The Room Gradle plugin rewrites the export silently on every build and `SchemaExportTest` moves with it, so nothing else in the project will tell you — verified 2026-08-06 by adding a column to `StudyEntity` and watching all 60 tests stay green.

Before the first release this is untidy. After one it is the bug where a device cannot open its own database.

## 2. PostgREST over HTTP — once per backend change, ~20 min

> **Run 2026-08-10. All four probes passed, and it found a real bug on the way in — see "Creating a study over HTTP" below.** Every row created was deleted afterwards; counts verified back to zero.

The role matrix (`supabase/tests/role_matrix.sql`) impersonates roles with `set_config` **inside SQL**. Nothing has ever exercised the path the app will actually take: PostgREST, publishable key, real JWT. A policy can be correct in SQL and still be reachable differently over HTTP.

> **This writes to production.** There is one database — dev, test and prod are the same remote project — and the matrix's `begin … rollback` trick is not available over HTTP. Every row you create here is real until you delete it. Do the cleanup step.

Setup — two users, one study:

```
export URL=https://<project-ref>.supabase.co
export KEY=<publishable key>

curl -s "$URL/auth/v1/signup" -H "apikey: $KEY" -H 'Content-Type: application/json' \
  -d '{"email":"probe-pi@example.test","password":"probe-pw-1"}'
curl -s "$URL/auth/v1/signup" -H "apikey: $KEY" -H 'Content-Type: application/json' \
  -d '{"email":"probe-collector@example.test","password":"probe-pw-2"}'
```

Use the publishable key, never `service_role`. If the project requires email confirmation these come back unconfirmed and the token call fails — confirm both in the dashboard, or create them there instead.

Grab a JWT per user from `/auth/v1/token?grant_type=password`, export as `PI_JWT` and `COL_JWT`, then as PI create a study (the `claim_new_study` trigger makes you PI), add the collector to `study_members`, and create a form, a form version and a participant.

The four probes that matter:

| # | As | Request | Expect |
|---|---|---|---|
| 1 | collector | `POST /rest/v1/submissions` own row | 201 |
| 2 | collector | `GET /rest/v1/submissions` | only their own rows |
| 3 | collector | `PATCH` a row collected by someone else | `[]` — **0 rows, not an error** |
| 4 | collector | `DELETE /rest/v1/submissions?id=eq.…` | 401/403 |

Probe 3 is the one to read carefully. A too-loose UPDATE policy paired with a correct SELECT policy returns success having changed nothing, which looks identical to a permission error from the client. That is the silent-update trap the role matrix covers at assertion 13; this checks it survives the HTTP path. Observed: `[]` with HTTP 200, which is right.

Probe 4 returns `42501 permission denied for table submissions` — the `DELETE` revoke, not a policy. That distinction matters: it holds even if someone later writes a careless DELETE policy.

### Creating a study over HTTP — found here, now fixed

**`POST /rest/v1/studies` used to fail with `Prefer: return=representation` and succeed with `return=minimal`.**

```
return=minimal        -> 201
return=representation -> 403  new row violates row-level security policy for table "studies"
```

`claim_new_study` was an **AFTER INSERT** trigger, and `RETURNING` applies the SELECT policy before AFTER-row triggers fire, so the creator was not yet a member of their own study. That breaks `.insert().select()`, the default `supabase-js` idiom.

Fixed in two migrations, because moving the trigger to BEFORE INSERT was necessary but not sufficient — `private.role_in_study` is `STABLE` and so cannot see a row written by its own statement's trigger. The second migration adds `private.role_in_study_now`, a `VOLATILE` twin used only by the `studies` SELECT policy. Verified: the same call now returns 201 with the row.

**The role matrix could not catch this**, which is the entire reason this section exists. It inserts studies as the table owner, so RLS never applies, and it never used `RETURNING` under a policy. Assertion 37 now closes that specific hole; the general blind spot remains, so keep probing over HTTP.

### Cleanup

Deleting the study cascades to members, forms, form versions, participants, submissions and audit rows; deleting the users cascades to identities and sessions. Both are doable over SQL as `postgres` — the dashboard is not required, despite what the Auth API suggests.

```sql
delete from public.studies where name = 'probe rls check';
delete from auth.users where email like 'probe.%@cairn.test';
```

Then verify rather than assume — count `auth.users`, `auth.identities`, `auth.sessions`, every `public` table and `storage.objects`. All zero.

## 3. Migration rehearsal — done 2026-08-08, do not repeat

Rehearsed end to end against a throwaway v2 and reverted. **`MigrationTestHelper` works under Robolectric.** Everything below is the answer, so the first real migration is typing rather than research.

> **Superseded in part, 2026-08-12.** There is now an `androidTest` source set in
> `core/database`, and `CairnDatabaseInstrumentedTest` runs `MigrationTestHelper`
> on a device, where the Room Gradle plugin wires the schemas into assets with no
> `Sync` task and no convention-directory workaround. Prefer that. The notes
> below still apply to the Robolectric route if it is ever wanted again, and the
> `SchemaExportTest` warning at the end applies either way.
>
> ```
> ./gradlew :core:database:connectedDebugAndroidTest
> ```
>
> Today it validates version 1 against itself, because there is no version 2.
> That is the half of a migration test that can exist before a migration does:
> it proves `schemas/1.json` describes the database this build actually creates
> on a device, which is what a real `MIGRATION_1_2` will be checked against.

The helper that resolves is the legacy Instrumentation-based one:

```kotlin
@get:Rule
val helper: MigrationTestHelper = MigrationTestHelper(
    instrumentation = InstrumentationRegistry.getInstrumentation(),
    databaseClass = CairnDatabase::class.java,
)

@Test
fun `a v1 database migrates to v2`() {
    helper.createDatabase(TEST_DB, 1).close()
    helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()
}
```

Four things that cost time, so they do not cost it twice:

- **The helper reads schemas from assets, and the Room Gradle plugin only wires them into `androidTest` assets.** A unit test fails with `FileNotFoundException: Cannot find the schema file in the assets folder`. Put them in the `src/test/assets/` convention directory — a `Sync` task from `schemas/`, gitignored, is the tidy version.
- **`android.sourceSets.getByName("test").assets.srcDir(…)` throws on AGP 9**: `DefaultAndroidLibrarySourceSet_Decorated cannot be cast to AndroidLibrarySourceSet`. Both the lambda and non-lambda forms fail. Use the convention directory instead of the DSL.
- **The helper uses the framework SQLite, not `BundledSQLiteDriver`.** Under Robolectric that means Robolectric's SQLite, which is *not* the engine the app ships. Fine for validating schema shape; do not read a passing migration test as proof the migration runs on-device.
- **On a version bump it is `SchemaExportTest`'s identity-hash assertion that fails, not its version assertion** — the version check reads `1.json`, which still says 1. Expect the hash comparison to go red and update the test deliberately.

Migrations are driver-based in Room 2.8 — `override fun migrate(connection: SQLiteConnection)`, not the old `SupportSQLiteDatabase`.

Verified non-vacuous: changing the migration to add a wrongly-named column fails the test.

Steps, when a real v2 arrives: add the column, bump `@Database(version = 2)`, write the `Migration`, build, confirm `2.json` appears and `1.json` is untouched, then the test above.

## 4. Role matrix + advisors — after any DDL, ~5 min

> **Last run 2026-08-10: 37/37 `ok`, cleanup counts all zero, zero security advisors.**
>
> If the project has hibernated, SQL fails with `28P01: password authentication failed for user "postgres"` and `get_advisors` says *"currently hibernated and will wake on next supported request"*. It came back on its own after a couple of days; a read to `/rest/v1/` did not wake it.


Not manual testing exactly, but it is manually triggered and nothing runs it for you.

Run `supabase/tests/role_matrix.sql` as **one batch** — it returns its assertions as a single result set on purpose, because a tool that surfaces only the last row-returning statement will otherwise show nothing and look like a pass.

**37 TAP lines, all `ok`.** Then confirm the suite left nothing behind:

```sql
select (select count(*) from auth.users), (select count(*) from public.submissions),
       (select count(*) from public.form_translations),
       (select count(*) from storage.objects where bucket_id='attachments'),
       (select count(*) from pg_extension where extname='pgtap');
```

All zero. Then run the security advisors.

## 5. Review against the live server — after any change to lock or void, ~10 min

> **Last run 2026-08-14: lock, void and restore all applied and audited.**

The review write path is the only place the app talks to the server outside the
sync engine, and its two most important behaviours cannot be asserted from the
app: that the server actually applies the change, and that a locked row then
refuses everything. Both need a real Postgres with real row-level security.

Sign in on the emulator as the **PI or coordinator** account — the collector has
no Review section, which is itself the first check.

1. Open a study you coordinate. It should grow a **Review** section under Forms.
   Open one you only collect in: no Review section.
2. **Submissions** lists every row in the study, whoever collected it, voided
   ones included and marked. The count line reads from the database, so it can
   legitimately be larger than the number of rows on screen.
3. Open a submission collected under an **older form version** than the one
   published now. It must render that version's fields and labels, not today's.
   This is the versioning claim, and it is the one worth checking by eye.
4. **Lock submission** → the dialog states the consequence → confirm. The chip
   flips to *Locked*, both buttons disappear, and the screen says unlocking is
   not possible.
5. Confirm in SQL:

```sql
select left(client_id::text, 8) as client, locked_at, updated_at, deleted_at
  from public.submissions order by collected_at desc;

select action, changed_at from public.submission_audit
 where action in ('lock', 'void', 'unvoid') order by changed_at desc;
```

`locked_at` is the device's clock at millisecond precision; `updated_at` is the
server's touch trigger at microsecond precision and will be a second or two
later. That split is expected — PostgREST cannot be asked to evaluate `now()`,
and nothing compares two `locked_at` values. The audit row is the authoritative
record of when it happened.

6. On a different, unlocked row: **Void submission** → confirm → the chip reads
   *Voided* and the only action offered is **Restore submission**. Restore it,
   and check both round-trips appear in the audit as `void` and `unvoid`.

**What cannot be checked from the app:** the refusal path. A locked row offers
no buttons and a collector is offered none, so there is no gesture that produces
a server refusal. The client half is tested against a fake that models the
server's rules, and the server half is the role matrix.

## Blocked, not skipped

Nothing, currently. The instrumented-test entry that lived here is done — the
`cairn` AVD exists, and 20 tests run on it across two source sets. The offline
claim has been driven by hand on a device as well: airplane mode on, collect,
reconnect, watch it reconcile.

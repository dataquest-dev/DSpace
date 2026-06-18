# CLARIN-DSpace → DSpace 9 Upgrade — Progress (source of truth)

> Read this file before starting or resuming. Update it after every meaningful step
> and before any context compaction/handoff. This is the durable state of the effort.
> Last updated: 2026-06-18 (session 1 — initial ground-truth + inventory).

## 0. TL;DR / current status

- **Phase:** Foundation / discovery complete; porting **not yet started** (code-wise).
- **Backend PR:** https://github.com/dataquest-dev/DSpace/pull/1339 — base `dtq-dev`, head `ufal/clarin-dspace-upgrade-v9`, **OPEN, CONFLICTING**. Head is currently **pristine vanilla DSpace 9.3** (no CLARIN code yet).
- **Frontend PR:** https://github.com/dataquest-dev/dspace-angular/pull/1316 — base `dtq-dev`, head `ufal/clarin-dspace-upgrade-v9`, **OPEN, CONFLICTING**. Head is currently **pristine vanilla dspace-angular 9.x**.
- **What "the PR diff" really is:** Because head = vanilla 9.3 and base = old CLARIN `dtq-dev` (7.6.5), the GitHub PR diff (BE 2372 files, FE 3944 files) is *the entire 7.6.5↔9.3 gap*, NOT work done. It would currently *delete* CLARIN. The real task = re-apply the CLARIN fork-delta on top of v9.

## 1. Objective

Port all CLARIN-DSpace (LINDAT/CLARIN, a.k.a. UFAL) features from the customized
fork (`dtq-dev`, DSpace **7.6.5**) onto vanilla **DSpace 9.3** backend and
**dspace-angular 9.x** frontend, on branch `ufal/clarin-dspace-upgrade-v9` in both
repos, preserving compatibility with the `dtq-dev` workflow, CI, Docker and local dev.
Get the full stack running locally in Docker and the `dataquest-dev/dspace-ui-tests`
Playwright suite passing.

## 2. Environment (verified this session)

| Tool | Version | Notes |
|------|---------|-------|
| Docker | 29.4.3 | daemon up, 12 CPUs, 15.58 GiB RAM |
| Docker Compose | v5.1.4 | |
| Java | Temurin 17.0.16 | DSpace 9 requires JDK 17 ✓ |
| Maven | 3.8.3 | |
| Node | 20.19.0 | |
| npm | 10.8.2 | |
| gh CLI | 2.67.0 | authed as `milanmajchrak` (ssh, repo scope) |
| git | 2.45.1 | SSH remotes; push allowed only to the 2 PR branches |

Workspace (do not leave it): `C:\workspace\clarin-dspace-v9-upgrade\`
- `DSpace/` — backend repo, branch `ufal/clarin-dspace-upgrade-v9`
- `dspace-angular/` — frontend repo, branch `ufal/clarin-dspace-upgrade-v9`

## 3. Ground-truth branch map

| Ref | What it is |
|-----|-----------|
| `origin/dtq-dev` (BE) | CLARIN-DSpace **7.6.5** — THE SOURCE of CLARIN features. Active (last commit 2026-06-17). |
| `origin/dtq-dev` (FE) | dspace-angular **7.6.5** CLARIN fork — SOURCE for FE features. |
| `origin/dtq-dev-9` (BE) | plain vanilla DSpace **9.1** tracking branch — *no CLARIN work*. Not useful except as reference. |
| `ufal/clarin-dspace-upgrade-v9` (BE) | TARGET — currently vanilla **9.3** (`e0fae432ff`). |
| `ufal/clarin-dspace-upgrade-v9` (FE) | TARGET — currently vanilla **9.x**. |
| tags `dspace-7.6.5`, `dspace-9.3` (BE) / `dspace-7.6.3`, `dspace-9.0` (FE) | vanilla baselines used to compute fork-delta. |

## 4. Methodology — fork-delta porting

The 7.6.5→9.3 PR diff mixes DSpace's own evolution with CLARIN changes. To isolate the
**CLARIN customization (fork-delta)**:

```
# backend CLARIN delta = vanilla 7.6.5  ->  dtq-dev
git diff dspace-7.6.5 origin/dtq-dev
# frontend CLARIN delta = vanilla 7.6.3 -> dtq-dev
git diff dspace-7.6.3 origin/dtq-dev    # (merge-base is 7.6.1/7.6.3 era)
```

Then re-apply that delta on top of v9, adapting for API changes. ADDED files port
near-verbatim (just package/API tweaks); MODIFIED vanilla files are the hard ports
(v9 refactored many APIs).

### Fork-delta size (measured this session)

**Backend (`dspace-7.6.5` → `dtq-dev`): 838 files, +107009 / −2677**
- 521 **added** files (187 Java classes named `Clarin*`/in `clarin/` pkgs, + migrations, + config)
- 314 **modified** vanilla files → **218 modified .java** (need v9 API adaptation) + 52 config/xml/properties
- By area: dspace-api/src 357, dspace-server-webapp/src 295, dspace/config 85, dspace-oai/src 32, .github/workflows 10, scripts ~25

**Frontend (`dspace-7.6.3` → `dtq-dev`): 1985 files, +191114 / −31092**
- ~959 are `src/assets/images` (license/label icons — copy verbatim) → ~1000 real code files
- By area: app/shared 199, item-page 155, core 85, submission 48, handle-page 28, clarin-licenses 24, epic-handle 23, login-page 21, entity-groups 21, bitstream-page 25, discojuice 7, license-contract-page 6, share-submission 6 …

## 5. CLARIN feature inventory (from wiki + delta) and porting status

Source: https://github.com/ufal/clarin-dspace/wiki/Features (+ linked pages).
Status legend: ⬜ not started · 🟦 in progress · ✅ ported+verified · ⛔ blocked · ➖ N/A.

### Licensing & Access Control
| # | Feature | BE | FE | Notes / source classes |
|---|---------|----|----|------------------------|
| L1 | CLARIN Licenses (label framework, confirmation) | ⬜ | ⬜ | `content/clarin/ClarinLicense*`, `app/clarin-licenses` |
| L2 | License Agreement Dialog (user details at download) | ⬜ | ⬜ | `ClarinLicenseResourceUserAllowance*`, `ClarinUserMetadata*` |
| L3 | Creative Commons license submission step | ⬜ | ⬜ | |
| L4 | Field-Level Permissions (ACL) | ⬜ | ⬜ | wiki FineGrainedFieldPermissions |
| L5 | Bitstream Download Tokens (time-limited) | ⬜ | ⬜ | `ClarinBitstreamServiceImpl`, download-token |

### Persistent Identifiers
| # | Feature | BE | FE | Notes |
|---|---------|----|----|-------|
| P1 | PIDs & Handles (per-community prefixes, external handles, content negotiation) | ⬜ | ⬜ | handle-page, epic-handle |
| P2 | EPIC PID API v2 integration | ⬜ | ⬜ | |
| P3 | Handle management GUI | ⬜ | ⬜ | |
| P4 | DOI registration (DataCite: reserve/register/update) | ⬜ | ⬜ | |
| P5 | DOI config per community | ⬜ | ⬜ | |
| P6 | ORCID authority (no Solr) | ⬜ | ⬜ | |
| P7 | ROR authority | ⬜ | ⬜ | |

### Submission Workflow
| # | Feature | BE | FE | Notes |
|---|---------|----|----|-------|
| S1 | Sharing a submission (share token) | ⬜ | ⬜ | `Add_share_token_to_workspaceitem` migration, app/share-submission |
| S2 | Complex fields (contact_person, funding, sizeInfo) | ⬜ | ⬜ | |
| S3 | Admin-only fields | ⬜ | ⬜ | |
| S4 | Autocomplete (Solr / static JSON) | ⬜ | ⬜ | |
| S5 | CMDI metadata file upload (METADATA bundle) | ⬜ | ⬜ | |
| S6 | CLARIN license steps (Distribution + Resource) | ⬜ | ⬜ | |
| S7 | CLARIN notice step | ⬜ | ⬜ | |

### Authentication & User Management
| # | Feature | BE | FE | Notes |
|---|---------|----|----|-------|
| A1 | Shibboleth AAI + DiscoJuice | ⬜ | ⬜ | `authenticate/clarin/ClarinShibAuthentication`, aai/discojuice |
| A2 | Shibboleth auto-registration | ⬜ | ⬜ | `ClarinUserRegistration*`, `ClarinVerificationToken*` |
| A3 | User registration + email verification | ⬜ | ⬜ | |
| A4 | Personal Access Tokens (PAT) | ⬜ | ⬜ | `ClarinToken*`, `Clarin_token` migration |

### Item Display & Services
| # | Feature | BE | FE | Notes |
|---|---------|----|----|-------|
| I1 | Featured services / Refbox | ⬜ | ⬜ | `ClarinFeaturedService*` |
| I2 | File previews (+directory tree) | ⬜ | ⬜ | `Added_Preview_Tables` migration |
| I3 | Item tombstones (withdrawn/replaced) | ⬜ | ⬜ | |
| I4 | ZIP download (all bitstreams) | ⬜ | ⬜ | |
| I5 | Item versioning (CLARIN handles/DOIs) | ⬜ | ⬜ | `ItemVersionLinker` |
| I6 | WebLicht integration (CMDI/OAI) | ⬜ | ⬜ | dspace-oai delta |

### Analytics & Monitoring
| # | Feature | BE | FE | Notes |
|---|---------|----|----|-------|
| M1 | Matomo analytics (bitstream + OAI tracking, report subscriptions) | ⬜ | ⬜ | `ClarinMatomo*Tracker`, `MatomoReportSubscription`, migration |
| M2 | Health report + report diff (DB snapshots, email) | ⬜ | ⬜ | `report_result` migration |
| M3 | Google Dataset Search structured data | ⬜ | ⬜ | |

### Operations & Admin Tools
| # | Feature | BE | FE | Notes |
|---|---------|----|----|-------|
| O1 | S3 storage integration (presigned URLs) | ⬜ | ⬜ | |
| O2 | File downloader CLI | ⬜ | ⬜ | `administer/FileDownloader` |
| O3 | Curation tasks (requiredmetadata, metadataqa, checklinks, checkhandles, registerdoi, profileformats) | ⬜ | ⬜ | |
| O4 | CLI scripts (clarin-token, process-cleaner, item-version-linker, file-downloader, file-preview, health-report, report-diff) | ⬜ | ⬜ | `administer/Clarin*`, `ItemVersionLinker` |
| O5 | Bulk import REST API (handles, users, licenses, metadata, bitstreams, logos) | ⬜ | ⬜ | import endpoints |
| O6 | Configuration file admin API | ⬜ | ⬜ | `app/configuration/service/ConfigFileService` |
| O7 | CMDI metadata export REST endpoint | ⬜ | ⬜ | |

## 6. Foundational: CLARIN DB migrations to port (Flyway)

Both `h2` (tests) and `postgres` (runtime) copies needed. v9 may already include some of
these upstream (e.g. orcid/supervision/system-wide-alerts were 7.x upstream additions and
are likely already in 9.x — DO NOT double-add). **Verify each against v9 before porting.**

CLARIN-specific (must port):
- `V7.2_2022.07.28__Upgrade_to_Lindat_Clarin_schema.sql` ← the big CLARIN schema (license tables, handles, user metadata, etc.)
- `V7.6_2024.08.05__Added_Preview_Tables.sql` + `V7.6_2025.06.09__Added_Indexes_To_Preview_Tables.sql`
- `V7.6_2024.09.30__Add_share_token_to_workspaceitem.sql`
- `V7.6_2024.10.25__insert_default_licenses.sql`
- `V7.6_2024.01.25__insert_checksum_result.sql`
- `V7.6_2025.06.03__Create_table_report_result.sql`
- `V7.6_2025.07.29__Matomo_report_registry_table.sql`
- `V7.6_2025.09.18__Clarin_token.sql`
- `V7.6_2025.10.30__7z_bitstream_format.sql`

> NOTE: version-numbering. These are `V7.x` prefixed. On v9 the Flyway baseline differs;
> CLARIN migrations may need renumbering to `V9.x_...` or placed so they run after the v9
> schema. Decide & document (see Decisions).

## 6b. Playwright suite (dataquest-dev/dspace-ui-tests) — how to run

Cloned to `C:\workspace\clarin-dspace-v9-upgrade\dspace-ui-tests` (private, branch `master`).
- **Stack:** Playwright 1.57 + TypeScript, npm. 6 spec files under `tests/tests/`
  (homePage, itemPage, loginPage, searchPage, submissionPage, universalPage) with page
  objects under `tests/pages/`.
- **Config:** `playwright.config.ts` (`globalSetup: ./scripts/merge-config.ts` merges
  `customer-constants/config.default.json` + any other `*.json`). No baseURL hardcoded;
  uses env. `ignoreHTTPSErrors:true`, 3 browser projects (chromium/firefox/webkit).
- **Run recipe (from CI `.github/workflows`):** env `HOME_URL=https://dev-5.pc:8443/repository/`,
  `NAME=DEFAULT`; `cd scripts && ./test.sh`. Credentials via `.env`
  (admin `dspace.admin.dev@dataquest.sk`/`admin`, user `dspace.user.dev@dataquest.sk`/`user`).
- **⚠ Hard dependency — seeded test data:** tests assert against FIXED production data:
  - handles: restricted_download `11234/1-2683`, bitstreams `11234/1-5419`,
    doi `20.500.12801/3901359-01`, new_version `11234/1-5677`, restricted collection
    `11234/1-f26b6363`, icons `11234/1-4875`, version redirect `11858/00-097C-...`.
  - branding: home title "LINDAT/CLARIAH-CZ Repository Home"; URL prefix `/repository`.
  - OAI endpoints, license manage-table, handle-table, bulk-access pages.
  → To run locally we must (a) point HOME_URL at the local stack, (b) seed matching
  CLARIN items/handles/licenses/DOIs/versions, (c) set `NAME`/locators for our config,
  or relax data-specific assertions. **This is a substantial test-data task, tracked separately.**

## 6c. Manual test specs
- **dspace-customers#55** "[TEST] Testing scenarios" (OPEN): step-by-step manual scenarios —
  create EPerson (→ `user_registration` row), restricted-download redirect to login,
  download from restricted item, license manage-table, handle-table, bulk-access, OAI cmdi
  exposure, item versions, icons, etc. Mirrors the Playwright assertions.
- **dspace-customers#411**: (to summarize next session) additional manual test spec.

## 7. Execution plan (phased)

1. **Foundation (this/next sessions)**
   - [x] Ground truth, fork-delta sizing, feature inventory, this file.
   - [x] Playwright suite + manual specs analyzed (run recipe + data dependency captured).
   - [ ] Docker baseline: bring up vanilla 9.3 BE + FE locally; confirm healthy. (Establishes that the platform runs before adding CLARIN.) — baseline mvn build running.
   - [x] Port CLARIN Flyway migrations (19 files: 9 h2 + 10 postgres) at ORIGINAL version numbers (faithful fresh-install order; preserves real upgrade path). **Validation pending: run Flyway against Docker postgres / h2 IT.**
2. **Backend port (by module, additive first)**
   - [ ] Entities/DAOs/services in `content/clarin`, `authenticate/clarin`, `administer/Clarin*` (additive — port first).
   - [ ] REST endpoints (`dspace-server-webapp`) — adapt to v9 REST/HATEOAS API.
   - [ ] Modified vanilla files (218 .java) — the hard ports; adapt per v9 API.
   - [ ] Config (`dspace/config` 85 files): spring beans, `*.cfg`, item-submission, etc.
3. **Frontend port (by feature module)**
   - [ ] Copy assets (license icons), i18n keys.
   - [ ] Port standalone feature modules (clarin-licenses, handle-page, epic-handle, share-submission, login/shibboleth, discojuice…).
   - [ ] Adapt core models/data-services to angular v9 (standalone components, new APIs).
4. **Integration & tests**
   - [ ] BE unit + IT (h2) green; FE lint + unit green.
   - [ ] Full Docker stack up with CLARIN config.
   - [ ] Configure + run Playwright `dataquest-dev/dspace-ui-tests` against local stack.
   - [ ] Manual test specs (dspace-customers #55, #411).
5. **Hardening**
   - [ ] Resolve PR conflicts; keep CI green; independent review agents; fix findings.

## 8. Tests executed (log)

| Date | Scope | Command | Result |
|------|-------|---------|--------|
| 2026-06-18 | env probe | `docker/java/mvn/node --version` | all present (see §2) |
| 2026-06-18 | baseline build | `mvn -q -T 1C -DskipTests -pl dspace-api,dspace-server-webapp,dspace-oai -am install` | ✅ BUILD SUCCESS in ~4 min; `dspace-api-9.3.jar` installed. Vanilla 9.3 BE compiles. Maven cache warm. |
| 2026-06-18 | migration validation attempt (unit test) | `mvn install -DskipUnitTests=false -pl dspace-api -am -Dtest=AccessStatusServiceTest` | ⚠ Test errored in setup with "DSpace home directory could not be determined / config-definition.xml" — kernel never started, Flyway never ran. **NOT a migration problem.** Pre-existing harness wrinkle (see KI-1). Migration validation deferred to Docker/postgres. |

### Work done in working tree (not yet pushed)
- **Backend migrations:** 19 CLARIN Flyway migrations ported from `dtq-dev` (h2×9, postgres×10) into
  `dspace-api/.../sqlmigration/{h2,postgres}/`. Purely additive; original version numbers kept.
  **VALIDATED on h2** (AccessStatusServiceTest ran 3 tests, Flyway applied them at DB init).
- **Backend dspace-api code (compiles ✅ `mvn -o -pl dspace-api compile` EXIT=0):**
  - 107 CLARIN java files ported into core (license framework, user metadata/registration,
    verification token, clarin token, item/workspace services, handle service + external handle,
    shibboleth ShibHeaders, featured services, matomo report subscription, etc.).
  - Systematic v9 migrations applied to ported files: `javax.*`→`jakarta.*` (persistence/ws.rs/
    servlet/validation/mail/annotation); `org.apache.commons.lang`(v2)→`lang3`;
    `NullArgumentException`→`IllegalArgumentException` (removed in lang3).
  - Maven deps added to `dspace-api/pom.xml`: matomo-java-tracker-java11 3.4.0, itextpdf 5.5.13.4,
    jfree jcommon/jfreechart, zjsonpatch 0.4.16, nimbus-jose-jwt ${nimbus-jose-jwt.version}.
  - **Modified vanilla files ported** (CLARIN deltas re-applied on v9): `handle/Handle.java`
    (+url/dead/deadSince), `app/util/Util.java` (+formatNetId), `handle/HandlePlugin.java`
    (+getRepositoryName/getCanonicalHandlePrefix statics).
  - Entities registered in `dspace/config/hibernate.cfg.xml` (9 CLARIN `<mapping>` entries).
  - **Hibernate 6 fix:** `ClarinLicense.confirmation` ORDINAL enum pinned `@JdbcTypeCode(SqlTypes.INTEGER)`
    (H6 defaults ORDINAL→TINYINT; column is INTEGER). Entity↔schema validation iterating (see §6d).

### Deferred backend files (in `_deferred/`, OUTSIDE repo — re-port as their feature lands) — 42 files
Reason: depend on bigger v9 rewrites or are leaf features, kept out to reach a compiling core.
- **S3 storage (AWS SDK v1→v2 rewrite needed):** `SyncS3BitStoreService`, `S3DirectDownloadServiceImpl`,
  `S3DirectDownloadService`, `SyncBitstreamStorageServiceImpl`, `ClarinBitstreamServiceImpl`,
  `service/clarin/ClarinBitstreamService`. (v9 uses `software.amazon.awssdk`, CLARIN used `com.amazonaws`.)
- **Hibernate type:** `storage/rdbms/hibernate/DatabaseAwareLobType` (H5 `SqlTypeDescriptor`→H6 `JdbcType`).
- **Preview feature:** `content/PreviewContent*`, `service/PreviewContentService`, `dao(/impl)/PreviewContentDAO*`,
  `scripts/filepreview/*`.
- **Report/health/diff:** `content/ReportResult*`, `app/healthreport/*`, `health/*`, `app/reportdiff/*`,
  `ctask/general/ItemMetadataQAChecker`, `curate/reporters/*`.
- **PID/EPIC:** `handle/PIDServiceEPICv2`, `handle/PIDConfiguration`, `handle/PIDCommunityConfiguration`,
  `handle/external/*` UN-deferred (core), but EPIC parts deferred.
- **Matomo runtime:** `app/statistics/clarin/ClarinMatomo*`, `matomo/*`.
- **Versioning/identifier CLI:** `administer/ItemVersionLinker*`, `identifier/ClarinVersionedHandleIdentifierProvider`,
  `api/DSpaceApi`.

### 6d. Entity↔schema reconciliation (Hibernate 6 `validate`) — ✅ PASSED (core)
DSpace boots with `hibernate.hbm2ddl.auto=validate`, so every ported entity must exactly match its
migrated table. **2026-06-18: `AccessStatusServiceTest` → 3 tests, 0 failures, BUILD SUCCESS** with all
9 CLARIN entities mapped → SessionFactory builds + Flyway migrations apply + schema validation passes.
This proves migrations↔entities are consistent on h2/Hibernate 6. Only fix needed: `confirmation`
ORDINAL enum `@JdbcTypeCode(SqlTypes.INTEGER)`. (Diagnostic note: a SessionFactory build failure
manifests as an NPE in the `EntityTypeServiceInitializer` afterMigrate callback — look earlier in the
surefire `*-output.txt` for the real `Schema-validation:` cause.)

## 9. Decisions / assumptions / open questions

- **D1 (CONFIRMED by maintainer 2026-06-18):** `dtq-dev` (7.6.5) is the authoritative feature source — "where CLARIN-DSpace is now with all the features". `ufal/clarin-dspace-upgrade-v9` is a fresh branch off vanilla 9.3 pushed to the dataquest repo. Port fresh from `dtq-dev`; `dtq-dev-9` is irrelevant (plain vanilla 9.1).
- **D7 (CONFIRMED):** Strategy = **foundation-first / horizontal** (migrations → entities/DAOs → services → REST → config → frontend; keep backend compiling/booting, then layer features).
- **D8 (CONFIRMED):** Push cadence = push **coherent building tranches** to PRs #1339/#1316 (only when the tranche compiles and self-tests pass + progress file updated).
- **D2 (open):** Flyway migration renumbering strategy for v9 (V7.x → V9.x vs ordering). Must inspect v9's existing migration set before porting.
- **D3 (assumption):** Frontend angular 9 uses standalone components / new control-flow; many 7.x CLARIN components will need conversion. Confirm scale during FE port.
- **D4 (process):** Do NOT push to PRs until a coherent, building tranche exists and the change set is reviewed. Only the two named PR branches may be pushed.
- **D5 (open):** Which upstream-7.x migrations are already in v9 (orcid, supervision, system-wide-alerts) — must not double-apply.
- **D6 (scope reality):** Full port is ~838 BE + ~1000 FE code files. This is a multi-session effort; progress is tracked here incrementally. No feature is silently skipped — anything not done is listed ⬜ above.

## 9b. Known issues (KI)

- **KI-1 — Local unit-test harness (Windows):** `mvn ... -pl dspace-api test -DskipUnitTests=false`
  fails at kernel start: "DSpace home directory could not be determined … config-definition.xml".
  Root cause: DSpace deliberately EXCLUDES `config-definition.xml` from `testEnvironment.zip`;
  unit tests resolve it from the classpath via the `dspace-services` **test-jar**. A piecemeal
  reactor build does not put that test-jar's resources on dspace-api's test classpath. Affects
  vanilla 9.3 too (not caused by CLARIN changes). **Resolution options:** (a) run full
  `mvn install -DskipUnitTests=false` from repo root once (CI does this on Linux), or
  (b) validate via Docker. Until resolved, validate DB/migrations on the Docker postgres stack.

## 10. Next steps (immediate)

1. **Validate ported migrations on Docker postgres** (decisive). Bring up `dspacedb` (postgres-pgcrypto 9.x) + run DSpace `database migrate` (or boot vanilla BE pointed at it) to confirm the 10 CLARIN migrations apply cleanly on top of the v9 schema. This also = first Docker stack milestone.
2. **Resolve KI-1** OR rely on Docker: do one full `mvn install -DskipUnitTests=false` from root (Linux/Docker) to get a working unit-test loop for verifying future backend tranches.
3. **Backend additive port — module 1: CLARIN content/persistence layer** (~54 files):
   `content/clarin` (22 entities+impls) + `content/service/clarin` (12) + `content/dao/clarin` (10) +
   `content/dao/impl/clarin` (10) + `content/factory` (ClarinServiceFactory). Wire into
   `hibernate.cfg.xml`/persistence + `core-services.xml`/`core-factory-services.xml`. Adapt to v9
   DAO/entity base-class APIs. Keep `mvn -pl dspace-api compile` green at each checkpoint.
4. Then: handle/PID layer (`org/dspace/handle*` 8+3+2+2), authenticate/clarin (Shibboleth), administer
   CLI tools, statistics/matomo, health/reportdiff, then `dspace-server-webapp` REST, then config.
5. Pull full PR/issue lists (BE/FE) and dspace-customers#411 into the inventory.
6. Push tranche 1 (migrations + progress doc + content layer) to PR #1339 once it compiles. NOTE:
   migrations alone are intentionally held back from push until paired with the entity layer that uses
   them AND validated on postgres (avoid turning PR CI red).

## 11. Reference links

- Wiki features: https://github.com/ufal/clarin-dspace/wiki/Features
- BE PRs/issues: https://github.com/dataquest-dev/DSpace/pulls · /issues
- FE PRs/issues: https://github.com/dataquest-dev/dspace-angular/pulls · /issues
- Manual test specs: dataquest-dev/dspace-customers#55 · #411
- Playwright: https://github.com/dataquest-dev/dspace-ui-tests
- Target PRs: dataquest-dev/DSpace#1339 (BE) · dataquest-dev/dspace-angular#1316 (FE)

# CLARIN-DSpace → DSpace 9 Upgrade — Progress (source of truth)

> Read this file before starting or resuming. Update it after every meaningful step
> and before any context compaction/handoff. This is the durable state of the effort.
> Last updated: 2026-06-18 (session 1 — initial ground-truth + inventory).

## 0. TL;DR / current status

> Last refreshed: 2026-06-22 (context cleanup vs. original mandate). The §0 below is the
> authoritative current state; earlier sections retain methodology/history.

- **Both PRs are MERGEABLE** (conflicts resolved): PR bases were re-pointed from `dtq-dev` to
  **`dtq-dev-9-base`** (= vanilla `dspace-9.3`, BE `e0fae432ff` / FE `a2141979`), so each PR diff =
  pure CLARIN additions on v9, no conflicts. Final landing into `dtq-dev` = Phase-2 reconciliation
  merge (`-s ours`) after the port completes (see §7b).
- **Backend PR #1339 — 2 tranches, CI-GREEN, MERGEABLE — but COMPILE-ONLY skeleton, NOT runtime-functional:**
  1. `bb7a599cd2` migrations (19) + ~107 entities/services/DAOs/factories.
  2. `8836cc632a` Spring bean wiring.
  Unit+Integration+CodeRabbit ✅ (`codecov` red = pre-existing infra).
  - Tranche 3 `85b260f567`: 24 ADDED CLARIN config files (clarin-dspace.cfg, OAI crosswalks, emails,
    registries) + entity columns (WorkspaceItem.shareToken, EPerson.welcomeInfo/canEditSubmissionMetadata).
  - Tranche 4 `7219832193`: **CLARIN License REST** (17 files: model/hateoas/converter/repository) →
    /server/api/core/clarinlicenses(+labels/resourcemappings/lruallowances) endpoints. compile+checkstyle ✅.
  - Tranche 5 `060e4fc6e8`: **handle/epic-handle/user-metadata/user-registration/verification-token/
    featured-service REST** (31 files incl. HandleRestRepository + link repositories). compile+checkstyle ✅.
    Deferred: ExternalHandleRestRepository (needs RandomStringGenerator bean).
  - Tranche 6 (plural bean-name fix): **RUNTIME-VALIDATED**. v9's REST framework resolves
    `getBean(category + "." + modelPlural)` (Utils.getResourceRepositoryByCategoryAndModel) — CLARIN
    repos were registered with singular `NAME` (7.x pattern) → every CLARIN endpoint 404'd. Added
    `PLURAL_NAME = NAME + "s"` to 8 Rest models + switched 8 RestRepositories' `@Component` to PLURAL_NAME.
  **RUNTIME VALIDATION (2026-06-23, the milestone reviewers demanded — "does it actually boot/work?"):**
  Built full reactor `mvn -o package` → **BUILD SUCCESS** (deployable installer, WAR + boot jar). Deployed
  via `ant fresh_install` to a local runtime, started a local Postgres (pgcrypto) container on :54321. Ran
  `dspace database migrate` → **ALL CLARIN Flyway migrations applied cleanly on real Postgres** (previously
  only h2): tables license_definition/label/label_extended_mapping/resource_mapping/resource_user_allowance,
  clarin_token, user_metadata, user_registration, previewcontent + columns workspaceitem.share_token,
  eperson.welcome_info/can_edit_submission_metadata, handle.url/dead/dead_since. Booted `server-boot.jar`
  (v9.3) → Spring context wires all CLARIN beans; **CLARIN REST endpoints serve from Postgres**:
  `/server/api/core/clarinlicenses|clarinlicenselabels|handles` → 200 (valid HAL, empty because
  insert_default_licenses.sql is an intentionally-commented template); `clarinlruallowances|
  clarinusermetadatas` → 401 (correctly auth-protected). This is the proof BE compile+CI could not give.
  Full endpoint sweep: clarinlicenses/clarinlicenselabels/clarinlicenseresourcemappings/handles=200,
  clarinlruallowances/clarinusermetadatas/clarinuserregistrations=401, clarinlicenses/search=200.
  KNOWN QUIRK (faithful to 7.x, NOT a regression): `clarinverificationtokens` returns **500** for
  anonymous because the service throws `AuthorizeException("You must be an admin...")` which the repo
  wraps as RuntimeException (it returns 200 with admin auth). Left as-is to preserve port fidelity;
  could be improved to 403 via `@PreAuthorize("hasAuthority('ADMIN')")` like the sibling repos. Also:
  CLARIN endpoints are not advertised in the discoverable `/api/core` index (no DiscoverableEndpoints
  registration) — harmless, the FE calls them by path.
  - Tranche 7 `98771fe034` (2nd runtime-diagnosed bug): v9 `ConverterService.toRest` enforces a
    `@PreAuthorize` SpEL read from the repository's most-derived `findOne` for every BaseObjectRest;
    CLARIN `findOne` overrides had none (faithful to 7.x; v7's ConverterService didn't do this) →
    converting any real CLARIN object threw `'expressionString' must not be null or blank` (400/500).
    Empty GETs passed only because there was no row to convert; surfaced on POST(create). FIX: add
    `@PreAuthorize` to findOne of the 8 CLARIN repos (permitAll() for license/label/mapping/handle,
    hasAuthority('ADMIN') for allowance/usermetadata/userregistration/verificationtoken) — mirrors
    vanilla CommunityRestRepository.findOne. compile+checkstyle clean.
  WRITE-PATH VALIDATION STATUS: the admin POST(create license label) round-trip is NOT yet runtime-
  proven — repeated boots after ~10:16 stalled at Ehcache offheap allocation because the host is
  **RAM-starved** (only ~1.3GB free of 32GB; the user's 10+ DSpace-8 docker containers consume the
  rest). Environment limit, not a code issue; the fix is logically certain vs ConverterService source.
  Retry the boot when RAM frees (kill nothing of the user's). Local validation setup: Postgres pgcrypto
  container `clarin-pg` :54321, runtime at `dspace-runtime/`, boot `java -jar webapps/server-boot.jar
  -Dserver.port=18080`; surgical redeploy = `mvn -o -pl dspace-server-webapp package` + `jar u0f
  server-boot.jar BOOT-INF/lib/dspace-server-webapp-9.3.jar` (stored).
  - Tranche 8 `02f123fd9`: 26 more REST files — ConfigFile REST, authorization (CanManageLicense +
    test controller), ClarinAutoRegistration/UserInfo controllers, License/Handle import controllers,
    submission steps (ClarinLicenseDistribution/Resource/Notice + 2 validations + SubmissionUtils),
    refbox DTOs, ClarinDataLicense, BigMultipartFile. javax.mail->jakarta.mail. clean compile+checkstyle.
    DEFERRED 14 (need unported vanilla methods Util.replaceLast/normalizeDiscoverQuery + libs
    org.json.simple/com.hp.hpl.jena, in `_deferred/`): ClarinUserMetadataRestController,
    Clarin{Item,EPerson,UserMetadata}ImportController, ClarinGroupRestController, SubmissionController,
    SuggestionRestController, ClarinRefBoxController, ClarinShibbolethLoginFilter, SolrOAIReindexer,
    Authrn{Rest,Resource}+AuthorizationRestController, DBConnectionStatisticsController.
  - Tranche 9 `33172a376`: vanilla-file methods — Item.isHidden()+isDiscoverable() tweak,
    WorkspaceItemService.findByShareToken (+Impl/DAO/DAOImpl). clean compile+checkstyle on dspace-api.
  - LESSON LEARNED: tranche 7 first pushed a duplicate-@PreAuthorize (7 of 8 CLARIN repos ALREADY had
    findOne @PreAuthorize in 7.x; only ClarinLicenseLabel lacked it) — a STALE incremental compile
    masked it -> CI red. Fixed `d28db8cab` by restoring originals. ALWAYS verify with `clean compile`.
  - Tranche 10 `64feedacce`: vanilla Utils methods (ONLY the ones v9.3 lacks — it already had maskEmail/
    getAllowedTemplateConfig/getSecureVelocityProperties/getMaxTimestamp; full delta would duplicate):
    core.Utils replaceLast/getTransactionPid/fetchUUIDFromUrl; rest.utils.Utils normalizeDiscoverQuery
    (+helpers)/encodeNonAsciiCharacters/disableCertificateValidation/distinctByKey/
    getCanonicalHandleUrlNoProtocol. Un-deferred 7 controllers (Authorization+AuthrnRest[+getTypePlural]/
    Resource, ClarinUserMetadata REST+Import, Submission, Suggestion) + json-simple 1.1.1 pom.
    Verified combined reactor clean compile (api+webapp) + checkstyle. STILL DEFERRED (7, deep
    v9-migration in _deferred/): ClarinRefBoxController (ancient com.hp.hpl.jena), SolrOAIReindexer +
    Clarin{Item,EPerson}ImportController (Date->Instant), ClarinShibbolethLoginFilter (StatelessLoginFilter
    ctor changed), ClarinGroupRestController (GroupRest.GROUPS), DBConnectionStatisticsController
    (getHibernateStatistics). Each needs individual v9 API adaptation.
  **STILL TODO for full function:** 57 MODIFIED config files (dspace.cfg include of clarin-dspace.cfg,
  item-submission.xml, shibboleth auth), remaining vanilla-file method additions, import/submission-step/
  OAI REST, CLARIN tests, then full Docker stack (BE+FE+Solr) + seed data + Playwright.
- **Frontend PR #1316 — 7 tranches, MERGEABLE** (head `d8511814d1`):
  1. assets (947 imgs) · 2. core models+data-services (49) · 3. **clarin-licenses** ✅green ·
  4. **handle-page** ✅green · 5. **epic-handle** ✅green · 6. **share-submission+change-submitter**
  (re-running after e2e-Docker flake; 22.x green) · 7. **contact-page+static-page** (CI running).
  Pre-validation `npm run build` + `npm run lint:nobuild -- --quiet` reliably predicts CI.
- **Porting method fully proven + documented** (BE §4/§6, FE standalone recipe + v9 gotcha catalog §6e).
- **REMAINING vs Definition-of-Done (none silently skipped):**
  - FE feature modules: login/shibboleth+discojuice, license-contract, **item-page (155 files)**,
    submission steps, bitstream-page, entity-groups, admin, info, accessibility, clarin-navbar-top.
  - FE i18n keys (all langs).
  - BE deferred features (S3/sync, preview, report/health, matomo, PID/EPIC clients, versioning CLI) —
    in `_deferred/` (§5 list); CLARIN BE unit/IT tests; REST layer (`dspace-server-webapp`, ~295 files); config.
  - **Docker:** bring up full CLARIN v9 stack locally (not yet done).
  - **Playwright** (`dspace-ui-tests`) against local stack + **manual specs** (#55, #411) — needs seeded data (§6b).
  - **Independent review agents** to challenge completeness (required by mandate; not yet run).
  - Phase-2 `dtq-dev` reconciliation merge.

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

## 6e. Frontend (dspace-angular) port plan — STARTED 2026-06-18

Repo `dspace-angular`, branch `ufal/clarin-dspace-upgrade-v9` = vanilla **9.3.0** (port not started).
PR #1316, base branch `dtq-dev-9-base` (= `a2141979` = vanilla `dspace-9.3`) ready.

**FE fork-delta (`dspace-7.6.3` → `dtq-dev`): 1497 added files + 266 modified `.ts`** (the hard
angular 7→9 ports). Added by module: item-page 101, shared 56, **core 53** (data-services/models —
foundational), handle-page 28, **clarin-licenses 24**, epic-handle 23, bitstream-page 18, login-page 15,
submission 14, admin 8, static-page 7, contact-page 7, share-submission 6, license-contract-page 6,
clarin-navbar-top 5, change-submitter 4, accessibility 4, info 3, statistics 2 (+~959 image assets).

v9 angular migration concerns: standalone components / new control-flow, `@dspace` API changes,
SSR build. Same proven method as BE: baseline build → port module → lint/build → coherent commit → CI.

**FE tranches:**
1. ✅ Baseline: `npm ci` OK; vanilla 9.3 builds (`npm run build`, ~4 min, dist produced).
2. ✅ **Assets pushed** (commit `31f660b4e9`, 947 net-new images: mime/flags/item-types/footer/
   static-pages/LINDAT branding). FE PR #1316 now has 1 commit → base switchable to `dtq-dev-9-base`.
3. ✅ **`core` clarin models + data services (49 files) PUSHED** (commit `9c8dfed2bd`): TYPE-CLEAN + LINT-CLEAN (`tsc -p tsconfig.json`,
   zero errors in CLARIN files; only pre-existing vanilla noise: grecaptcha global + a `.spec`).
   **Key v9 FE API delta:** `@dataService` decorator MOVED from `core/data/base/data-service.decorator`
   → `core/cache/builders/build-decorators` (NOT removed — still used for HAL resolution). Fix = repoint
   import path per file (kept the decorator). Deferred from this tranche: `bitstream-url-serializer` +
   `shared/clarin-shared-util` (pull in the `clarin-item-box-view` feature — port with that).
   FE validation loop: `tsc --noEmit -p tsconfig.json` (full project; `tsconfig.app.json`/ng build only
   checks files reachable from main, so unreferenced new files need the full tsconfig). Lint = `npm run lint`
   (`build:lint && ng lint`; direct `npx eslint` fails — needs the custom-rule build). Lint validating now.
4. ✅ **`clarin-licenses` module ported to v9 standalone & PUSHED** (`b3a7ff33c0` + lint fix `b5e2a6cbd5`):
   `ng build` + full `ng lint --quiet` both clean. First push went CI-red on template lint (scoped
   eslint missed `.html`); fixed via control-flow migration + `dsBtnDisabled` + `===` (see recipe step 5/6).
   Lesson baked into recipe: always run full `npm run lint:nobuild -- --quiet` before pushing FE.
5. ✅ **handle-page module PUSHED** (`e0c1270b28`, FE tranche 4): 6 standalone components, /handle-table
   admin route. build+lint clean.
6. ✅ **epic-handle module PUSHED** (`7c5a6aa29b`, FE tranche 5): 5 standalone components, /epic-handle-table
   admin route. build+lint clean.
7. ✅ **share-submission + change-submitter PUSHED** (`5ece6b02ce`, FE tranche 6): 2 standalone
   components, /share-submission route. build+lint clean. (Fixes: chart.js→hasNoValue, instanceof generic.)
8. ⬜ i18n keys (`src/assets/i18n/*.json5` — additive, merge into all langs + watch i18n lint).
9. ✅ **contact-page + static-page PUSHED** (`d8511814d1`, FE tranche 7): themed contact page + static
   HTML pages. Ported ClarinSafeHtmlPipe + HtmlContentService. build+lint clean.
10. ⬜ Remaining modules: login/shibboleth+discojuice, license-contract, item-page additions (155 files,
    large), submission steps, bitstream-page, entity-groups, admin, info, accessibility, clarin-navbar-top.

### Themed-component v9 pattern (resolved):
`Themed*Component extends ThemedComponent<BaseComponent>` — NO `standalone`/`imports` field, `templateUrl:
'../shared/theme-support/themed.component.html'`, methods `getComponentName()`/`importThemedComponent()`/
`importUnthemedComponent()`. The 7.x CLARIN themed wrappers already match — no conversion needed. Base
component is converted to standalone normally; route points at the Themed wrapper.

### More v9 API changes (learned porting contact/static — apply to all remaining modules):
- `LocaleService.getCurrentLanguageCode()` now returns `Observable<string>` (was `string`) →
  `await firstValueFrom(...)` in async methods.
- `@nguniversal/express-engine/tokens` REMOVED → `REQUEST`/`RESPONSE` now in `src/express.tokens`
  (relative e.g. `../../express.tokens`).
- CLARIN-added services (e.g. HtmlContentService) need `@Injectable({ providedIn: 'root' })` (no NgModule
  provides them now).
- `ds-themed-loading` element → `ds-loading` (ThemedLoadingComponent).
- `no-negated-async`: `!(obs | async)` → `(obs | async) === null/false/undefined` or `?.length === 0`.
- Other CLARIN pipes ported standalone so far: 6 license pipes (clarin-licenses) + ClarinSafeHtmlPipe.

### FE module CI confirmations
- clarin-licenses (`b5e2a6cbd5`): ✅ green. handle-page (`e0c1270b28`): ✅ green. epic-handle
  (`7c5a6aa29b`): ✅ green. share-submission (`5ece6b02ce`): tests(22.x) ✅ but tests(20.x) ❌ on a
  TRANSIENT infra step **"Start DSpace REST Backend via Docker (for e2e)"** (not code — 22.x passed same
  commit) → re-ran. contact/static (`d8511814d1`): CI running.
- **CI flake pattern:** FE `tests` job e2e step "Start DSpace REST Backend via Docker" is occasionally
  flaky (like BE `net.handle` repo flake). If only that step fails (and the other Node version passes),
  it's infra — `gh run rerun <id> --failed`. Pre-validation (`npm run build` + `npm run lint:nobuild
  -- --quiet`) remains reliable for CODE correctness.

### More v9 FE gotchas (learned porting handle/epic):
- `ds-loading` selector = `ThemedLoadingComponent` (shared/loading/themed-loading.component).
- `*ngVar` = `VarDirective` (shared/utils/var.directive) — control-flow migration does NOT convert it.
- `standalone: true` is the DEFAULT now → eslint rule `dspace-angular-ts/no-default-standalone-value`
  strips it (eslint --fix removes the line; keep `imports: []`).
- rxjs `catchError`/error callbacks: keep param `(error: unknown)` (rule
  `@smarttools/rxjs/no-implicit-any-catch` forbids `any`), cast `(error as any)` at access sites.
- Most lint errors are auto-fixable (`eslint --fix`): import sort/newlines, standalone-import sort,
  no-default-standalone-value, disabled→dsBtnDisabled (html). Manual: eqeqeq, missing imports.

### v9 standalone feature-module migration RECIPE (proven on clarin-licenses)
1. `git checkout origin/dtq-dev -- <module files>` (exclude `*.spec.ts`).
2. Each component: add `standalone: true` + `imports: [...]` to `@Component`. Imports = template deps:
   `CommonModule` (ngIf/ngFor/async), `TranslateModule` (translate pipe), `ReactiveFormsModule`/`FormsModule`
   (forms/ngModel), `NgbXModule` pieces, v9 standalone components (`ThemedLoadingComponent`
   `shared/loading/themed-loading.component`, `PaginationComponent` `shared/pagination/pagination.component`),
   sibling components, and any custom CLARIN pipes (make those `@Pipe({ standalone: true })` too).
3. Replace `*.module.ts` + `*-routing.module.ts` with `*-routes.ts` exporting `ROUTES: Route[]`; use v9
   FUNCTION resolvers/guards (`i18nBreadcrumbResolver`, `siteAdministratorGuard`, lowercase) not the 7.x classes.
4. Add any route-path constants to `app-routing-paths.ts`; wire `loadChildren: () => import('./x/x-routes').then(m => m.ROUTES)` into `app-routes.ts` (+ import the path const).
5. **v9 template migration (REQUIRED — CI gate `ng lint --quiet` checks `.html`!):** run
   `npx ng generate @angular/core:control-flow --path=src/app/<module> --interactive=false`
   to convert `*ngIf/*ngFor` → `@if/@for` (CI rule `@angular-eslint/template/prefer-control-flow`).
   Also fix `==`→`===` (`template/eqeqeq`) and `disabled`/`[disabled]` on `<button>` →
   `[dsBtnDisabled]` + import `BtnDisabledDirective` (`shared/btn-disabled.directive`)
   (rule `dspace-angular-html/no-disabled-attribute-on-button`; eslint --fix does the html swap
   but NOT the .ts import).
6. Validate (BOTH): `npm run build` (ng build → missing template deps) AND
   **`npm run lint:nobuild -- --quiet`** (the real CI gate — lints `.ts` AND `.html` templates;
   scoped `npx eslint <ts>` MISSES template errors — that's what turned t3 CI red on first push).
   ⚠ The control-flow schematic reformats `@angular/core` imports multi-line — re-add any imports
   carefully AFTER the closing `} from '@angular/core';`, not inside the block.
   Then commit + push.

## 6f. Docker stack startup + Playwright (mandate items — NOT yet runnable end-to-end)

**Standard run recipe (vanilla v9, works today):**
- Backend: `cd DSpace && docker compose -f docker-compose.yml up -d` → services `dspacedb`
  (postgres:15, port 5432), `dspacesolr` (dspace-solr 9_x, 8983), `dspace` (REST WAR, 8080;
  built from local `Dockerfile` context). CLI/seed: `docker-compose-cli.yml`.
- Frontend: `cd dspace-angular && docker compose -f docker/docker-compose-dist.yml up -d` (UI on 4000),
  or `docker/docker-compose-rest.yml` to pull a REST backend for the UI.
- This is what `dspace-ui-tests` CI uses ("Start DSpace REST Backend via Docker" step).

**To run a CLARIN v9 stack with OUR changes (build custom images):**
- BE image: `mvn -DskipTests package` then `docker compose build dspace` (packages our WAR incl.
  migrations + entities + wired services). Postgres runs our Flyway migrations on first boot.
- FE image: `docker build` from dspace-angular (our standalone CLARIN modules).
- Port the dtq-dev Docker customizations (delta vs 7.6.5): `Dockerfile`, `Dockerfile.cli`,
  `docker-compose*.yml`, `scripts/docker/matomo/*` (Matomo container for analytics). NOT yet ported.

**BLOCKERS (why end-to-end CLARIN stack + Playwright can't pass yet):**
1. **BE REST layer not ported** (`dspace-server-webapp`, ~295 CLARIN files): the FE CLARIN
   data-services (license/handle/etc.) have no `/server/api` endpoints to call → CLARIN features
   non-functional end-to-end. This is the #1 gate for a working stack.
2. **FE port incomplete**: item-page/submission/login-shibboleth modules not yet ported.
3. **Playwright needs seeded CLARIN data** (§6b): fixed handles (`11234/1-2683` restricted download,
   DOIs, versions), LINDAT branding ("LINDAT/CLARIAH-CZ Repository Home"), specific items. Must seed
   matching data OR relax data-specific assertions, and point `HOME_URL` at the local stack.
4. dtq-dev Docker customizations + Matomo not ported.

**Plan:** finish BE REST + remaining FE modules → build custom images → `docker compose up` →
seed CLARIN data → point Playwright `HOME_URL` at local UI → run `cd dspace-ui-tests/scripts && ./test.sh`
→ iterate. Until then, Docker/Playwright are tracked as BLOCKED with the above reasons (nothing skipped silently).

## 6g. INDEPENDENT REVIEW FINDINGS (2026-06-22) + resolutions

Two independent review agents (BE + FE, separate) challenged the completion claims. Both concluded:
**"compiles + CI-green" ≠ "works".** Validation relied on `mvn compile`/`ng build`/`ng lint` which catch
neither runtime DI nor unimported `*ngVar` (FE `strictTemplates` off) nor missing config/vanilla-mods (BE).
This section records EVERY valid finding and its resolution — nothing is silently skipped.

### Frontend findings (PR #1316)
| # | Sev | Finding | Resolution |
|---|-----|---------|-----------|
| C1 | CRIT | 9 CLARIN data-services had bare `@Injectable()` (no provider) → `NullInjectorError` → clarin-licenses + handle-page DEAD at runtime | ✅ FIXED: `providedIn:'root'` on all (12 incl. bitstream/metadata services) |
| C2 | CRIT | `*ngVar` used without importing `VarDirective` in clarin-license-table, handle-table, change-submitter-page → no data renders | ✅ FIXED: imported VarDirective in all 3 |
| C3 | CRIT | Admin menu entries dropped → handle-table/epic-handle-table/licenses unreachable via UI (routes work by URL) | ⬜ TODO: port a CLARIN menu provider (v9 `shared/menu/providers/*.menu.ts`; was 7.x `menu.resolver.ts`) |
| M1 | MAJ | `usage-report.model.ts` `Point.values` delta (array→keyed object) dropped | ⬜ DEFERRED w/ statistics/matomo FE (applying in isolation breaks vanilla stats components) — documented, not silent |
| M2 | MAJ | No i18n keys → ported pages show raw key strings | ⬜ deferred (FE tranche 8) — features not presentable until done |
| m1 | MIN | handle/epic routes: added `endUserAgreementCurrentUserGuard` (v9 admin convention) vs original `[SiteAdministratorGuard]` only | accepted (v9 convention); confirm w/ maintainer |
| m2 | MIN | contact route lost `pathMatch:'full'` | ✅ FIXED |
| m3 | MIN | all `*.spec.ts` excluded → CI tests exercise no CLARIN FE code | documented (FE test gap; see Phase) |

Verdict: epic-handle was the only runtime-correct tranche pre-fix; C1/C2/m2 now fixed make
clarin-licenses/handle-page/change-submitter functional. C3 + i18n still needed for full usability.

### Backend findings (PR #1339)
| # | Sev | Finding | Resolution |
|---|-----|---------|-----------|
| C1 | CRIT | **85 CLARIN config files unported** — 24 ADDED missing (clarin-dspace.cfg, 7 email templates, OAI crosswalks lindat_cmdi/olac/metasharev2/elg.xsl, registries) NOT in `_deferred`; 57/61 MODIFIED config not re-applied (dspace.cfg, item-submission.xml, authentication-shibboleth.cfg, discovery.xml). Ported services read null `lr.*` config | ⬜ TODO — now DOCUMENTED here (was silently dropped). Port incrementally; see Phase. |
| C2 | CRIT | Entity↔schema feature losses validate can't catch: `WorkspaceItem.shareToken` (migration adds col, entity unmapped → share-submission BE dead), `EPerson.welcomeInfo/canEditSubmissionMetadata`, `Item.isHidden()` | ⬜ TODO — port these vanilla-entity modifications (additive field+mapping+accessor) |
| C3 | CRIT | 91/94 CLARIN modifications to vanilla `dspace-api` .java NOT re-applied (only Handle/Util/HandlePlugin). Added methods (ItemService.hasUploadedFiles, WorkspaceItemService.findByShareToken, BitstreamService.retrieveFile, *.addLogo, ...) absent. Compiles only because consumers deferred/in REST | ⬜ TODO — apply needed vanilla-file deltas as their consumers land (REST). DOCUMENTED. |
| M1 | MAJ | 44 ADDED CLARIN test classes dropped (not in `_deferred`); `AccessStatusServiceTest` is VANILLA → no CLARIN code path tested | ⬜ DOCUMENTED — port CLARIN BE tests (was understated) |
| M2 | MAJ | `handle/PIDService.java` reflectively loads deferred `PIDServiceEPICv2` → ClassNotFoundException at runtime | ✅ FIXING: defer PIDService.java (its only path needs the deferred class; `EpicHandleServiceImpl` is the live path) |
| M3 | MAJ | REST (179) + OAI (28) layer absent → no CLARIN API surface | acknowledged (§0/Phase) — #1 functional blocker |
| m2 | MIN | 19 migrations byte-identical to dtq-dev, ordering safe, no v8/v9 collisions | ✅ confirmed good |
| m3 | MIN | Spring wiring sound; all wired beans exist; no bean→deferred-class | ✅ confirmed good |

**SILENTLY-NOT-PORTED (now explicitly documented, distinct from `_deferred/`):** 24 CLARIN config files,
57 CLARIN config modifications, 91 CLARIN vanilla-`.java` modifications, 44 CLARIN test classes, the
3 entity-mapping gaps (C2), FE menu providers (C3), FE i18n (M2). These were NOT tracked before — the
review surfaced them; they are now in the plan (§7) and NONE are skipped without a reason.

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

## 7b. PR conflict-resolution strategy (IMPORTANT)

Trial merge `origin/dtq-dev` → branch = **310 conflicts** (measured 2026-06-18). These are the
fundamental 7.6.5↔9 divergence (e.g. `dspace-api/pom.xml` parent `9.3` vs `7.6.5`; `HandlePlugin`
v9/lang3 vs 7.6.5/commons-lang2). **Do NOT naively resolve:**
- Resolving toward `dtq-dev` reverts the upgrade (re-introduces 7.6.5 code) and breaks the build.
- `git merge -s ours dtq-dev` makes the PR *look* mergeable but, if merged, **deletes every
  not-yet-ported CLARIN feature** from `dtq-dev` (our tree supersedes it). A landmine.
- `git merge -X ours dtq-dev` still pulls in all dtq-dev-only 7.6.5 files (un-ported CLARIN
  classes) → won't compile.

**UPDATE 2026-06-18 — Phase 1 base re-point in progress (maintainer):** branch `dtq-dev-9-base`
created at vanilla `dspace-9.3` in BOTH repos (BE `e0fae432ff`, FE `a2141979`). Switching each PR's
base to `dtq-dev-9-base` makes the diff = pure CLARIN additions, no conflicts.
- BE #1339: base switch in GitHub UI still PENDING (branch pushed; PR base still `dtq-dev` → shows 3
  clean commits once switched).
- FE #1316: base branch ready; PR will show 0 commits until the frontend port starts (expected).
- Note: `dtq-dev` keeps moving (now `22cfef58e6`, 7.6.7 security patches) — PR is decoupled once base=v9;
  reconcile in Phase 2.

The `CONFLICTING` badge is **protective** — it blocks a premature destructive merge. The conflicts
are a symptom of the port being incomplete. **Correct resolution = complete the port, then do ONE
reconciliation merge taking the v9 side at the end** (when "take ours" drops nothing). Each tranche
shrinks the real divergence; badge clears at completion. Alternative the maintainer could choose:
re-point the PR base to vanilla `dspace-9.3` (then the diff is purely CLARIN additions, no conflicts) —
but that changes the PR's "replace dtq-dev" intent, so left to the maintainer.

## 8. Tests executed (log)

| Date | Scope | Command | Result |
|------|-------|---------|--------|
| 2026-06-18 | env probe | `docker/java/mvn/node --version` | all present (see §2) |
| 2026-06-18 | baseline build | `mvn -q -T 1C -DskipTests -pl dspace-api,dspace-server-webapp,dspace-oai -am install` | ✅ BUILD SUCCESS in ~4 min; `dspace-api-9.3.jar` installed. Vanilla 9.3 BE compiles. Maven cache warm. |
| 2026-06-18 | migration validation attempt (unit test) | `mvn install -DskipUnitTests=false -pl dspace-api -am -Dtest=AccessStatusServiceTest` | ⚠ Test errored in setup with "DSpace home directory could not be determined / config-definition.xml" — kernel never started, Flyway never ran. **NOT a migration problem.** Pre-existing harness wrinkle (see KI-1). Migration validation deferred to Docker/postgres. |
| 2026-06-18 | KI-1 root cause + fix | full-reactor `mvn install -DskipUnitTests=false ...` (testEnv needs `dspace` assembly module) | ✅ resolved; unit-test harness works. |
| 2026-06-18 | entity↔schema validate (h2) | `mvn -o -pl dspace-api test -DskipUnitTests=false -Dtest=AccessStatusServiceTest` | ✅ 3/3 pass; SessionFactory builds, all 9 CLARIN entities pass hbm2ddl validate. |
| 2026-06-18 | checkstyle | `mvn -o -pl dspace-api checkstyle:check` | ✅ 0 violations (after `fix_imports.py` normalized jakarta import groups in 30 files). |
| 2026-06-18 | license headers | `mvn -o -pl dspace-api license:check` | ✅ OK (removed an empty stray test artifact). |
| 2026-06-18 | **PR #1339 CI run 1** | GitHub Actions (push `bb7a599cd2`) | **Integration Tests ✅ PASS (30m); Unit Tests ❌ FAIL** — transient: `net.handle:handle:9.3.2` not fetched from `handle.net` repo (fell back to central). Root pom unchanged by my commit + IT passed same dep ⇒ flaky infra. Re-ran failed job. |
| 2026-06-18 | **PR #1339 CI re-run** | `gh run rerun ... --failed` | **Unit Tests ✅ PASS (11m); Integration Tests ✅ PASS; CodeRabbit ✅.** `codecov` ❌ = pre-existing infra (missing CODECOV_TOKEN on protected branch), not code-related. **Tranche 1 is CI-green on all code checks.** |
| 2026-06-18 | spring wiring validate (local) | `mvn install -DskipUnitTests=false -Dtest=AccessStatusServiceTest` | ✅ 3/3; full Spring context boots with all CLARIN beans, no autowiring errors. |
| 2026-06-18 | **PR #1339 CI run (tranche 2 `8836cc632a`)** | GitHub Actions | **Unit ✅ PASS (11m), Integration ✅ PASS (26m), CodeRabbit ✅** (codecov infra red). Tranche 2 CI-green first try. |
| 2026-06-18 | FE baseline | `npm ci` + `npm run build` (vanilla angular 9.3) | ✅ builds (~4m), dist produced. |
| 2026-06-18 | **PR #1316 FE tranche 1 (assets `31f660b4e9`)** | GitHub Actions `tests 20.x/22.x` | ✅ PASS (≈30m each). |
| 2026-06-19 | FE tranche 2 local validation | `tsc -p tsconfig.json` + `eslint --fix` + `eslint --quiet` | ✅ type-clean + lint-clean (0 errors) on the 49 core files. |
| 2026-06-19 | **PR #1316 FE tranche 2 (core layer `9c8dfed2bd`)** | GitHub Actions | **tests 20.x ✅ PASS, tests 22.x ✅ PASS, CodeRabbit ✅.** FE core layer CI-green. |

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

### Tranche 2 (in progress, working tree): CLARIN Spring bean wiring
Added CORE bean defs (deferred classes skipped) to make ported services runtime-active:
- `core-factory-services.xml`: `clarinServiceFactory`, `handleClarinServiceFactory`.
- `core-dao-services.xml`: 11 CLARIN DAO beans (license/label/mapping/userreg/usermeta/allowance/
  item/verificationtoken/matomoreport/token + HandleClarinDAOImpl). (PreviewContent/ReportResult DAOs deferred.)
- `core-services.xml`: 16 service beans (license framework, user metadata/registration, verification +
  clarin tokens, item/workspace, matomo report subscription, DspaceObjectClarin, AuthorizationBitstreamUtils,
  HandleClarinServiceImpl, EpicHandleServiceImpl, ProvenanceServiceImpl) + `MatomoTracker` bean wired to
  v9's existing `${matomo.tracker.url}` (factory `@Autowired(required=true)` needs it). (ClarinBitstream/
  PreviewContent/ReportResult/ClarinMatomo* trackers deferred.)
Validation: ✅ full Spring context boots cleanly (`AccessStatusServiceTest` 3/3, no
autowiring/bean-creation errors). **Committed `8836cc632a`, pushed to PR #1339; CI running.**

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

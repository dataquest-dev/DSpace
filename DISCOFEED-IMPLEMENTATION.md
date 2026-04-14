# DiscoFeed / IdP Discovery Implementation Guide

## Objective

Implement a backend endpoint that serves a cached, transformed list of Identity Providers (IdPs) from the Shibboleth Service Provider's DiscoFeed. This powers a frontend IdP discovery widget for Shibboleth-based federated login.

---

## Architecture Overview

```
Shibboleth SP                      DSpace Backend                        Frontend
┌──────────────┐    HTTP GET     ┌─────────────────────┐   GET /api/    ┌──────────────┐
│ /Shibboleth   │ ◄──────────── │ DiscoFeedsDownload   │   discojuice/ │ IdP Discovery│
│  .sso/        │  (server-to-  │ Service (fetches &   │   feeds       │ Widget       │
│  DiscoFeed    │   server)     │ transforms JSON)     │ ◄──────────── │              │
└──────────────┘                └─────────┬───────────┘               └──────┬───────┘
                                          │                                  │
                                          ▼                                  │ User picks IdP
                                ┌─────────────────────┐                      │
                                │ DiscoFeedsUpdate     │                      ▼
                                │ Scheduler (cron      │         Redirect to /Shibboleth.sso
                                │ cache refresh)       │         /Login?entityID=...&target=...
                                └─────────┬───────────┘
                                          │
                                          ▼
                                ┌─────────────────────┐
                                │ DiscoFeedsController │
                                │ GET /api/discojuice/ │
                                │ feeds                │
                                └─────────────────────┘
```

---

## What You Need to Create

### 3 Java Files

All three files go under `dspace-server-webapp/src/main/java/org/dspace/app/rest/` (pick a suitable sub-package, e.g., `repository/` or `discojuice/`).

---

### 1. `DiscoFeedsController.java`

**Purpose:** REST controller that serves the cached IdP feed JSON.

**Requirements:**

- `@RestController` with `@RequestMapping("/api/discojuice/feeds")`
- Single `GET` handler
- Must be publicly accessible — no authentication required (`@PreAuthorize("permitAll()")` or equivalent)
- Returns the cached JSON string from the scheduler (see below)
- Content-Type: `application/json`
- If cache is empty/null, return HTTP 503 or an empty JSON array `[]`

**Pseudocode:**

```java
@RestController
@RequestMapping("/api/discojuice/feeds")
public class DiscoFeedsController {

    @Autowired
    private DiscoFeedsUpdateScheduler scheduler;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> getFeeds() {
        String content = scheduler.getFeedsContent();
        if (StringUtils.isBlank(content)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("[]");
        }
        return ResponseEntity.ok(content);
    }
}
```

---

### 2. `DiscoFeedsUpdateScheduler.java`

**Purpose:** Background scheduled task that periodically fetches and caches the IdP feed.

**Requirements:**

- Spring `@Component` that implements `InitializingBean` (or use `@PostConstruct`)
- On startup: fetch the feed immediately (so the endpoint is populated before the first cron tick)
- On schedule: use `@Scheduled(cron = "${discojuice.refresh}")` to periodically refresh
- Must check `shibboleth.discofeed.allowed` config key — if `false`, skip fetching entirely
- Stores the fetched+transformed content in a `String` field (in-memory cache)
- Exposes a `getFeedsContent()` method for the controller

**Config keys used:**

| Key | Example Value | Purpose |
|-----|---------------|---------|
| `shibboleth.discofeed.allowed` | `true` | Feature toggle — if `false`, the feed is never fetched and the endpoint returns empty |
| `discojuice.refresh` | `0 */5 * * * *` | Cron expression for how often the feed is refreshed (every 5 minutes in this example) |

**Pseudocode:**

```java
@Component
public class DiscoFeedsUpdateScheduler implements InitializingBean {

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private DiscoFeedsDownloadService downloadService;

    private String feedsContent;

    @Scheduled(cron = "${discojuice.refresh}")
    public void refreshFeeds() {
        boolean allowed = configurationService
            .getBooleanProperty("shibboleth.discofeed.allowed", false);
        if (!allowed) {
            return;
        }
        feedsContent = downloadService.downloadAndTransformFeeds();
    }

    @Override
    public void afterPropertiesSet() {
        refreshFeeds();  // load on startup
    }

    public String getFeedsContent() {
        return feedsContent;
    }
}
```

---

### 3. `DiscoFeedsDownloadService.java`

**Purpose:** Fetches raw IdP metadata JSON from the Shibboleth SP's DiscoFeed endpoint, transforms it into a compact format, and returns it as a JSON string.

**Requirements:**

- Spring `@Service`
- Reads the DiscoFeed URL from config key `shibboleth.discofeed.url`
- Makes an HTTP GET request to that URL (server-to-server, typically `http://localhost/Shibboleth.sso/DiscoFeed`)
- Parses the response as a JSON array
- Transforms each IdP entry (shrink transform — see below)
- Deduplicates by `entityID`
- Returns the result as a JSON string (array of transformed IdP objects)

**Config key used:**

| Key | Example Value | Purpose |
|-----|---------------|---------|
| `shibboleth.discofeed.url` | `https://myserver.example.com/Shibboleth.sso/DiscoFeed` | URL of the Shibboleth SP's DiscoFeed handler |

#### Raw Input Format (from Shibboleth SP)

Each IdP entry in the raw DiscoFeed JSON array looks like:

```json
{
  "entityID": "https://idp.example.org/idp/shibboleth",
  "DisplayNames": [
    { "value": "Example University", "lang": "en" },
    { "value": "Ukázková Univerzita", "lang": "cs" }
  ],
  "Descriptions": [
    { "value": "IdP of Example University", "lang": "en" }
  ],
  "InformationURLs": [ ... ],
  "Logos": [
    { "value": "https://...", "height": 16, "width": 16 },
    { "value": "https://...", "height": 80, "width": 80 }
  ]
}
```

#### Shrink Transform

For each IdP, produce a compact object:

```json
{
  "entityID": "https://idp.example.org/idp/shibboleth",
  "title": "Example University",
  "country": "_all_"
}
```

**Transform rules:**

1. Keep `entityID` as-is
2. Build `title` from `DisplayNames`: concatenate all `value` fields (e.g., `"Example University, Ukázková Univerzita"`) — or use the first one. This is what the frontend displays.
3. Set `country` to `"_all_"` (a static fallback — country-based filtering is optional and can be added later)
4. **Strip** all other fields: `Logos`, `InformationURLs`, `Descriptions`, `PrivacyStatementURLs` — these are not needed and add significant payload size
5. **Deduplicate** by `entityID` — if the same entityID appears more than once, keep only the first occurrence

#### Expected Output Format

```json
[
  {
    "entityID": "https://idp.example.org/idp/shibboleth",
    "title": "Example University",
    "country": "_all_"
  },
  {
    "entityID": "https://idp2.example.org/idp/shibboleth",
    "title": "Another University",
    "country": "_all_"
  }
]
```

**JSON parsing:** Use the `json-simple` library (`org.json.simple`), which is already a dependency of the `dspace-server-webapp` module. Alternatively, use Jackson (`com.fasterxml.jackson`) which is also available via Spring Boot.

**HTTP client:** Use `java.net.HttpURLConnection`, Apache `HttpClient`, or Spring's `RestTemplate` / `WebClient` — whichever is idiomatic in the existing codebase.

---

## Configuration Keys Summary

Add these 3 keys to DSpace configuration (e.g., `local.cfg` or a dedicated module config file):

```properties
# Enable/disable the DiscoFeed endpoint (must be true for the feed to work)
shibboleth.discofeed.allowed = true

# URL of the Shibboleth SP's DiscoFeed handler (server-to-server)
shibboleth.discofeed.url = https://myserver.example.com/Shibboleth.sso/DiscoFeed

# Cron expression for cache refresh (every 2 minutes in this example)
discojuice.refresh = 0 */2 * * * *
```

---

## Full Login Flow (Frontend → Backend → Shibboleth → Backend)

This is the end-to-end flow the frontend widget initiates:

1. **Frontend loads IdP list:** `GET /server/api/discojuice/feeds` → receives JSON array of IdPs
2. **User picks an IdP** from the widget (selects an `entityID`)
3. **Frontend redirects the browser** to:
   ```
   /Shibboleth.sso/Login?entityID={URL-encoded-entityID}&target={URL-encoded-callback}
   ```
   Where `target` is:
   ```
   /server/api/authn/shibboleth?redirectUrl={URL-encoded-final-destination}
   ```
4. **Shibboleth SP** redirects the user to the selected IdP's login page
5. **User authenticates** at the IdP
6. **IdP posts SAML assertion** back to the Shibboleth SP
7. **Shibboleth SP** sets session headers and redirects to the `target` URL
8. **DSpace's `ShibbolethLoginFilter`** at `/api/authn/shibboleth` picks up the Shibboleth headers (`SHIB-*`, `eppn`, `mail`, etc.), creates/matches a DSpace EPerson, issues a JWT auth cookie
9. **DSpace redirects** to the `redirectUrl` parameter (the frontend page the user started from)

### Redirect URL Construction (for the frontend)

```
https://{server}/Shibboleth.sso/Login
  ?entityID={encodeURIComponent(selectedIdp.entityID)}
  &target={encodeURIComponent(
    "https://{server}/server/api/authn/shibboleth?redirectUrl=" + 
    encodeURIComponent(window.location.href)
  )}
```

---

## Shibboleth SP Prerequisites

The Shibboleth SP (`shibboleth2.xml`) must have:

1. **DiscoFeed handler** enabled:
   ```xml
   <Handler type="DiscoveryFeed" Location="/DiscoFeed"/>
   ```

2. **MetadataProvider(s)** configured — these determine which IdPs appear in the feed:
   ```xml
   <MetadataProvider type="XML" uri="https://metadata.federation.org/metadata.xml"
                     backingFilePath="federation-metadata.xml" reloadInterval="7200">
   </MetadataProvider>
   ```
   Each `<MetadataProvider>` element points to an IdP or federation metadata source. Only IdPs from configured providers appear in `/Shibboleth.sso/DiscoFeed`.

3. **SSO element** that allows `entityID` override on the Login query string:
   ```xml
   <SSO entityID="https://default-idp.example.org/..." discoveryProtocol="SAMLDS">
       SAML2
   </SSO>
   ```
   The `entityID` attribute here sets the default IdP, but when the frontend passes `?entityID=...` on `/Shibboleth.sso/Login`, it overrides this default.

---

## Existing DSpace Authentication Infrastructure

These already exist in vanilla DSpace and do NOT need to be created:

- **`ShibbolethLoginFilter`** — Spring Security filter at `/api/authn/shibboleth` that handles the Shibboleth callback (reads headers, creates EPerson, sets JWT cookie, redirects)
- **`ShibAuthentication`** — authentication plugin that processes Shibboleth attributes
- **`WebSecurityConfiguration`** — registers the Shibboleth login filter in the filter chain
- **`authentication-shibboleth.cfg`** — configuration for Shibboleth header mapping, lazy session, auto-registration

The Shibboleth authentication module configuration (`authentication-shibboleth.cfg`) must be properly set:

```properties
plugin.sequence.org.dspace.authenticate.AuthenticationMethod = org.dspace.authenticate.ShibAuthentication

authentication-shibboleth.lazysession = true
authentication-shibboleth.lazysession.loginurl = /Shibboleth.sso/Login
authentication-shibboleth.netid-header = eppn
authentication-shibboleth.email-header = mail
authentication-shibboleth.autoregister = true
```

---

## Validation Checklist

After implementation, verify:

1. **Build succeeds:** `mvn clean install -DskipTests=true --no-transfer-progress -P-assembly`
2. **No compile errors** in `dspace-server-webapp`
3. **Controller is reachable:** `GET /server/api/discojuice/feeds` returns HTTP 200 with JSON array (or 503 if feed not yet loaded)
4. **Public access:** The endpoint does NOT require authentication
5. **Config toggles work:** Setting `shibboleth.discofeed.allowed = false` causes the endpoint to return `[]` or 503
6. **Scheduler runs:** Confirm the cron expression fires and the cache is populated
7. **Startup load:** The feed is available immediately after application startup (no need to wait for first cron tick)
8. **JSON format:** Each entry has `entityID` (string), `title` (string), `country` (string) — no extra fields from the raw feed
9. **Deduplication:** No duplicate `entityID` values in the response
10. **Shibboleth login flow:** Browser redirect to `/Shibboleth.sso/Login?entityID=...&target=...` completes the SAML flow and returns to DSpace with auth cookie set
11. **Checkstyle passes:** `mvn checkstyle:check -f dspace-server-webapp/pom.xml --no-transfer-progress`
12. **No security issues:** The DiscoFeed URL should point to a trusted source (typically localhost or the same server); the endpoint doesn't expose sensitive data

---

## File Placement Summary

```
dspace-server-webapp/src/main/java/org/dspace/app/rest/
  └── (pick sub-package, e.g., discojuice/)
      ├── DiscoFeedsController.java
      ├── DiscoFeedsUpdateScheduler.java
      └── DiscoFeedsDownloadService.java

dspace/config/local.cfg  (or appropriate config location)
  + shibboleth.discofeed.allowed = true
  + shibboleth.discofeed.url = https://...
  + discojuice.refresh = 0 */2 * * * *
```

---

## Important Notes

- The endpoint path `/api/discojuice/feeds` is a convention from the reference implementation. You may adjust it, but the frontend must match.
- The `country` field is set to `"_all_"` as a static fallback. If country-based filtering is needed later, it can be derived from the IdP's metadata or GeoIP lookup.
- The `title` field is what the frontend displays to users. Include multiple language variants concatenated if available, or pick the primary language.
- The scheduler's cron expression `discojuice.refresh` uses Spring's 6-field cron format (seconds included): `second minute hour day month weekday`.
- If `@Scheduled` does not accept a property expression with a missing default gracefully, provide a sensible default: `@Scheduled(cron = "${discojuice.refresh:0 */5 * * * *}")`.

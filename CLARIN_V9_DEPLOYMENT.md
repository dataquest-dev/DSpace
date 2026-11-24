# CLARIN-DSpace v9: deploy with Docker

How to run a v9 instance (`dtq-dev-9-base` or a v9 release tag) on a server.

The v7 guide in the UFAL wiki ([Installation](https://github.com/ufal/clarin-dspace/wiki/NewInstallation))
does not work for v9. On `dtq-dev-9-base` the compose files in `dspace-angular/docker/` are almost the
vanilla ones (we add only `SERVER_SERVLET_CONTEXT_PATH` and `SOLR_HEAP`). They ignore the v7 `.env`
(`INSTANCE`, `REST_URL`, `S3_*`, ...), publish fixed ports on all interfaces and allow only one instance
per host. This guide keeps those files and adds a small overlay per instance.

For a local development stack see [CLARIN_LOCAL_RUNBOOK.md](CLARIN_LOCAL_RUNBOOK.md).

## Before you start

- Docker with Compose 2.24.4 or newer. The overlay uses `!override` and `!reset` (check with
  `docker compose version`).
- A checkout of `dataquest-dev/dspace-angular` on `dtq-dev-9-base`. Only its `docker/` folder is used.
- One folder per instance, for example `/opt/dspace-envs/5`, with the files below.
- RAM per instance: backend 4 GB + Solr 4 GB (both tunable below) + the UI runs one Node process per
  CPU core. Plan for about 10 GB plus the UI.
- The image tag must exist in **both** `dataquest-dev/DSpace` and `dataquest-dev/dspace-angular`. Only
  `dtq-dev-9-base` is built on every push; a release tag is built by `tag-release`. The old
  `customer-*` tags are v7 Tomcat images and will not start on v9.

## 1. Instance files

### `.env`

```sh
# Image tag: dtq-dev-9-base or a v9 release tag that exists in both repos
DOCKER_OWNER=dataquest
DSPACE_VER=dtq-dev-9-base

# Containers get this suffix: dspace5, dspacedb5, dspacesolr5, dspace-angular5
INSTANCE=5
COMPOSE_PROJECT_NAME=dspace-5
INSTANCE_DIR=/opt/dspace-envs/5

# Host ports for nginx. Unique per instance.
DSPACE_FE_PORT=4005
DSPACE_DOCKER_REST_PORT=8085
# Bind host ports to this interface. 127.0.0.1 when nginx is on the same machine;
# set the reachable IP when the proxy is on another host.
HOST_IP=127.0.0.1
# Docker network 172.25.0.0/16. Unique per instance.
DSPACE_SUBNET=172.25

DSPACE_NAME=My Repository
TIMEZONE=Europe/Prague
SOLR_HEAP=2g

# Public URLs (what the browser uses)
REST_URL=https://repo.example.org/server
UI_URL=https://repo.example.org
DSPACE_REST_SSL=true
DSPACE_REST_HOST=repo.example.org
DSPACE_REST_PORT=443
DSPACE_REST_NAMESPACE=/server
# The UI renders on the server only for requests whose Host matches this URL. Get it wrong and
# crawlers and link previews get an empty page while browsers still look fine.
DSPACE_UI_BASEURL=https://repo.example.org
```

### `docker-compose-rest.yml` (backend, database, Solr)

```yaml
networks:
  dspacenet:
    ipam:
      config: !override
        - subnet: ${DSPACE_SUBNET}.0.0/16
services:
  dspace:
    container_name: dspace${INSTANCE}
    restart: unless-stopped
    environment:
      TZ: ${TIMEZONE}
      dspace__P__name: ${DSPACE_NAME}
      dspace__P__server__P__url: ${REST_URL}
      dspace__P__ui__P__url: ${UI_URL}
      proxies__P__trusted__P__ipranges: ${DSPACE_SUBNET}.0
      # the v9 entrypoint is plain `java -jar`, it ignores JAVA_OPTS
      JAVA_TOOL_OPTIONS: -Xmx4g
    ports: !override
      - published: ${DSPACE_DOCKER_REST_PORT}
        target: 8080
        host_ip: ${HOST_IP}
    volumes:
      - ${INSTANCE_DIR}/local.cfg:/dspace/config/local.cfg
      - dspace_logs:/dspace/log
  dspacedb:
    container_name: dspacedb${INSTANCE}
    restart: unless-stopped
    environment:
      TZ: ${TIMEZONE}
    ports: !reset []
  dspacesolr:
    container_name: dspacesolr${INSTANCE}
    restart: unless-stopped
    ports: !reset []
volumes:
  dspace_logs:
```

The database and Solr are not published to the host. Reach the database with
`docker exec -it dspacedb5 psql -U dspace dspace`.

### `docker-compose.yml` (UI)

```yaml
services:
  dspace-angular:
    container_name: dspace-angular${INSTANCE}
    restart: unless-stopped
    # our production image is the plain tag, there is no -dist tag
    image: docker.io/${DOCKER_OWNER}/dspace-angular:${DSPACE_VER}
    ports: !override
      - published: ${DSPACE_FE_PORT}
        target: 4000
        host_ip: ${HOST_IP}
```

### `local.cfg`

Mounted as `/dspace/config/local.cfg`. Create it before the first start, otherwise Docker creates a
folder with that name. The defaults in `dspace.cfg` and `clarin-dspace.cfg` are placeholders or point
at dataquest, so set at least these per instance:

```
# This instance
dspace.shortname = My Repository

# Mail. Without a working server, registration, password reset, request-a-copy and
# subscription-send all fail silently.
mail.server = smtp.example.org
mail.server.port = 25
mail.from.address = noreply@example.org
mail.admin = admin@example.org
# default is dspace.admin.dev@dataquest.sk
lr.help.mail = admin@example.org

# Handles. The default prefix 123456789 is fake: those handles never resolve and are hard to
# take back once cited. Use your registered prefix.
handle.prefix = YOUR_PREFIX
handle.canonical.prefix = https://hdl.handle.net/
lr.pid.community.configurations = community=*, prefix=YOUR_PREFIX, type=local, canonical_prefix=https://hdl.handle.net/, subprefix=2

# Matomo is on by default and points at a dummy host. Turn it off or set real values.
matomo.track.enabled = false

# Assetstore on local disk only. clarin-dspace.cfg turns sync on, which assumes an S3 mirror;
# without one, every new file is recorded as stored twice while only one copy exists.
sync.storage.service.enabled = false
```

Without a Shibboleth SP in front of this instance, also turn Shibboleth login off (see
[Shibboleth](#6-shibboleth)):

```
plugin.sequence.org.dspace.authenticate.AuthenticationMethod = org.dspace.authenticate.PasswordAuthentication
```

For S3 (store `1` in `bitstore.xml`):

```
assetstore.index.primary = 1
assetstore.s3.enabled = true
assetstore.s3.bucketName = my-bucket
assetstore.s3.subfolder = assetstore
assetstore.s3.awsAccessKey = ...
assetstore.s3.awsSecretKey = ...
assetstore.s3.awsRegionName = us-east-1
assetstore.s3.endpoint = https://s3.example.org
assetstore.s3.useRelativePath = true
```

With sync on, every file still lands in the local `assetstore` volume too. Set
`sync.storage.service.enabled = false` for S3 only. Note: when `endpoint` is set, path-style access is
forced on and cannot be turned off, so a provider that needs virtual-host style will not work.

## 2. Start

```sh
git clone -b dtq-dev-9-base https://github.com/dataquest-dev/dspace-angular
cd dspace-angular
I=/opt/dspace-envs/5
DC="docker compose --env-file $I/.env -f docker/docker-compose-dist.yml -f docker/docker-compose-rest.yml -f $I/docker-compose-rest.yml -f $I/docker-compose.yml"
$DC pull
$DC up -d --no-build
```

Keep the file order. The repo's `docker/docker-compose-rest.yml` defines the network in most detail, so
it comes after `docker-compose-dist.yml`. `docker-compose-dist.yml` runs the production UI; the repo's
`docker/docker-compose.yml` is the dev one and is not used here.

The first start creates the database schema. The backend answers after a minute or two:

```sh
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8085/server/api
```

Create the first administrator. It asks for the password:

```sh
docker exec -it dspace5 /dspace/bin/dspace create-administrator -e admin@example.org -f Admin -l User -c en
```

## 3. Nginx

The v7 wiki block does not work here. It uses `listen 80`, sets no `Host` header (which turns off
server-side rendering), has no upload limit and does not forward the scheme. Use a full block:

```nginx
server {
    listen 443 ssl;
    server_name repo.example.org;

    ssl_certificate     /etc/ssl/repo.example.org.crt;
    ssl_certificate_key /etc/ssl/repo.example.org.key;

    # clarin-dspace.cfg allows uploads up to 4 GB; the nginx default is 1 MB.
    client_max_body_size 4g;

    location / {
        # SSR renders only when Host matches DSPACE_UI_BASEURL, so pass the real host.
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_pass http://127.0.0.1:4005;
    }

    location /server/ {
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_request_buffering off;
        proxy_pass http://127.0.0.1:8085;
    }

    # CMDI metadata for machines (see v7 wiki for the detail)
    if ($arg_format ~* "cmdi") {
        rewrite ^/handle/(.*)$ /server/cmdi/oai-metadata?metadataPrefix=cmdi&handle=$1? last;
    }
    if ($http_accept = "application/x-cmdi+xml") {
        rewrite ^/handle/(.*)$ /server/cmdi/oai-metadata?metadataPrefix=cmdi&handle=$1? last;
    }
}
```

Replace `4005` / `8085` with this instance's `DSPACE_FE_PORT` / `DSPACE_DOCKER_REST_PORT`. Without
`X-Forwarded-Proto` the backend thinks it is on http behind your TLS, which breaks the secure CSRF
cookie and, with a namespace, CORS.

Check SSR is on (must print `0`):

```sh
curl -s https://repo.example.org/ | grep -c '<ds-app></ds-app>'
```

## 4. Cron

The same jobs as v7, only the container name differs:

```
# /etc/cron.d/dspace-5
MAILTO=root
RUN="docker exec dspace5 /dspace/bin/dspace"
0 23 * * * root $RUN oai import
20 0 * * * root $RUN index-discovery
1 3 * * * root $RUN subscription-send -f D
2 3 * * 0 root $RUN subscription-send -f W
3 3 1 * * root $RUN subscription-send -f M
0 4 1 * * root $RUN cleanup -v
30 0 * * * root $RUN health-report -e admin@example.org
```

## 5. Data from a v7 instance

The v7 dump lists CLARIN 7.6 migrations that v9 does not have. Flyway refuses to migrate until
`database repair` marks them as deleted. These steps follow the dev-6 import job
(`.github/workflows/deploy.yml` in `dspace-angular`, `dtq-dev-9-base`). Run them from the
`dspace-angular` folder with `DC` set as in step 2.

On the v7 instance, make the dump:

```sh
docker exec dspacedb<N> pg_dump -U dspace dspace > dump.sql
```

On the v9 host:

```sh
$DC stop dspace
docker cp dump.sql dspacedb5:/tmp/dump.sql
docker exec dspacedb5 bash -c "dropdb -U dspace dspace && createdb -U dspace --owner=dspace --encoding=UNICODE dspace && psql -U dspace -d dspace -f /tmp/dump.sql"
$DC run --rm --no-deps --entrypoint /bin/bash dspace -c "/dspace/bin/dspace database repair && /dspace/bin/dspace database migrate ignored"
$DC start dspace
docker cp /path/to/assetstore dspace5:/dspace/
docker exec dspace5 /dspace/bin/dspace index-discovery -b
docker exec dspace5 /dspace/bin/dspace oai import -c
```

The source folder must be called `assetstore`. No `chown` is needed, the v9 image runs as root.
`dropdb` removes the admin from step 1, so recreate it afterwards. Copy the old `handle-server` folder
too if the instance has its own prefix (see below), and run `solr-import-statistics` if you carried the
usage stats.

## 6. Shibboleth

Shibboleth login is **on by default** (`clarin-dspace.cfg` lists `ClarinShibAuthentication`), and it
reads the user from request headers: `eppn`, `mail`, `givenName`, `sn`, `SHIB-SCOPED-AFFILIATION` and
`Shib-Identity-Provider`. With auto-register on, anyone who can send those headers to
`/server/api/authn/shibboleth` logs in as any federated user. The proxy is the only thing that stops a
client from forging them.

- **No SP in front:** turn Shibboleth off in `local.cfg` (shown above). Password login still works.
- **With an SP:** the Shibboleth SP must set those attributes, the proxy must clear any client-supplied
  copy of each header before it reaches the backend, and `/server/api/authn/shibboleth` must be
  reachable only through the SP.

## 7. Handle server

v7 started the handle server in the backend entrypoint; v9 does not. An instance that mints its own
handles needs it. Add to `docker-compose-rest.yml` under the `dspace` service, and set `handle.prefix`
in `local.cfg`:

```yaml
    entrypoint: !override
      - /bin/bash
      - '-c'
      - |
        while (!</dev/tcp/dspacedb/5432) > /dev/null 2>&1; do sleep 1; done;
        /dspace/bin/dspace database migrate
        /dspace/bin/start-handle-server
        java -jar /dspace/webapps/server-boot.jar
    ports: !override
      - published: ${DSPACE_DOCKER_REST_PORT}
        target: 8080
        host_ip: ${HOST_IP}
      - published: 2641
        target: 2641
        protocol: tcp
      - published: 2641
        target: 2641
        protocol: udp
    volumes:
      - ${INSTANCE_DIR}/local.cfg:/dspace/config/local.cfg
      - dspace_logs:/dspace/log
      - handle_server:/dspace/handle-server
```

Add `handle_server:` to the `volumes:` block. When migrating a v7 instance, copy its `handle-server`
folder (keys and config) into this volume, otherwise every existing hdl.handle.net link dies.

## Namespace (serve under a subpath)

To serve the instance under `/repository`, change these in `.env`:

```sh
REST_URL=https://repo.example.org/repository/server
UI_URL=https://repo.example.org/repository
DSPACE_REST_NAMESPACE=/repository/server
DSPACE_UI_NAMESPACE=/repository
DSPACE_UI_BASEURL=https://repo.example.org/repository
SERVER_SERVLET_CONTEXT_PATH=/repository/server
DSPACE_REST_SSRBASEURL=http://dspace:8080/repository/server
```

Add to the `dspace` service environment in `docker-compose-rest.yml`:

```yaml
      dspace__P__server__P__ssr__P__url: http://dspace:8080/repository/server
```

CORS is set from `dspace.ui.url`, which now carries a path, but a browser `Origin` never does. Add to
`local.cfg` the origin without the path:

```
rest.cors.allowed-origins = https://repo.example.org
```

Nginx then proxies `/repository/` and `/repository/server/`, and the health-check URL becomes
`http://127.0.0.1:8085/repository/server/api`.

## What changed against v7

- No Tomcat. The backend runs as `java -jar /dspace/webapps/server-boot.jar`, so the Tomcat symlink for
  the namespace is gone; use `SERVER_SERVLET_CONTEXT_PATH`.
- `JAVA_OPTS` has no effect on the backend; set the heap with `JAVA_TOOL_OPTIONS`.
- The v9 compose has no `S3_*` variables; S3 goes to `local.cfg`. `s3.upload.by.parts.enabled` is gone.
- The UI image tag has no `-dist` suffix. `docker-compose-dist.yml` without the overlay pulls a tag that
  does not exist.
- The handle server is not started (see above).
- Logs are in `/dspace/log` (volume `dspace_logs`), not under Tomcat. Containers run in UTC unless you
  set `TZ`.
- Data lives in the volumes `dspace-5_pgdata`, `dspace-5_assetstore` and `dspace-5_solr_data`. Back
  them up, and never `down -v`. On large instances put the assetstore on its own disk.
- To change UI config, mount your file at `/app/config/config.prod.yml`.
- Without Docker: Java 17, Node 20 to 24 with npm instead of yarn, Solr 9 (the image uses 9.8),
  PostgreSQL 15. Otherwise follow the
  [DSpace 9 installation docs](https://wiki.lyrasis.org/display/DSDOC9x/Installing+DSpace).

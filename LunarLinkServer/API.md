# LunarLink Server API

- Base URL: `http(s)://<host>:<port>`
- All request and response bodies are JSON (`Content-Type: application/json`), except file downloads.
- Field names are `snake_case`. Enum values are lowercase strings.
- Timestamps are Unix seconds (UTC).
- Sizes are bytes (`u64`).
- HTTPS is expected to be provided by a reverse proxy in front of the server.

---

## Contents

1. [Authentication](#authentication)
2. [Errors](#errors)
3. [Paths, roots and permissions](#paths-roots-and-permissions)
4. [Common objects](#common-objects)
5. [Endpoints](#endpoints)
   - [Public](#public): `GET /`, `GET /api/info`
   - [Auth](#auth): `POST /api/auth/login`, `GET /api/auth/me`
   - [Filesystem](#filesystem): `roots`, `stat`, `list`, `search`, `download`, `thumbnail`, `file`, `dir`, `copy`, `move`, `delete`
6. [Configuration reference](#configuration-reference)

---

## Authentication

Get a token from `POST /api/auth/login`, then send it on every protected request:

```
Authorization: Bearer <token>
```

- The token is a JWT (HS256) signed with `auth.jwt_secret`. It only identifies the user (`sub`).
  Permissions are **not** stored in the token. They are looked up in the loaded config on every request,
  so after you edit the config and restart the server, existing tokens get the new permissions and
  tokens of removed users are rejected.
- Tokens expire after `auth.token_ttl_minutes`. An expired token returns `401 TOKEN_EXPIRED`, and the
  client should log in again.
- Changing `jwt_secret` invalidates every issued token.
- Failed logins are rate-limited **per username**. After 5 consecutive failures, further attempts for that
  username are rejected with `429 TOO_MANY_ATTEMPTS` for an increasing backoff period (capped at 5 minutes).
  A successful login resets the counter, and failures older than 15 minutes are forgotten. Only usernames
  that exist in the config are tracked; unknown usernames always get `401 LOGIN_FAILED`.
- If `auth.enabled = false`, requests without an `Authorization` header run as the user `anonymous` with all
  permissions. Root permissions still apply. The server logs a warning at startup. `GET /api/info` reports
  `auth_required: false` so clients can skip the login screen.

---

## Errors

Every error response uses this shape:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "File or folder not found.",
    "details": null
  }
}
```

- `code` is stable and machine-readable. Clients should switch on `code`, not `message`.
- `message` is human-readable and may change. It never contains internal paths or OS error text.
- `details` is optional extra data (an object or `null`).

| HTTP | `code` | When |
|---|---|---|
| 400 | `BAD_REQUEST` | Malformed JSON, missing or invalid fields |
| 400 | `INVALID_PATH` | Path is empty, relative, contains NUL, or uses `..` to climb above the filesystem root |
| 400 | `NOT_A_DIRECTORY` | A directory operation (e.g. `list`) was used on a non-directory |
| 400 | `IS_A_DIRECTORY` | A file operation (e.g. `download`) was used on a directory |
| 400 | `INVALID_OPERATION` | Copying or moving a folder into itself, a destination that contains the source, or deleting, moving or replacing a root |
| 401 | `UNAUTHENTICATED` | No `Authorization` header (and auth is enabled) |
| 401 | `INVALID_TOKEN` | Header isn't `Bearer <token>`, bad signature, or the user no longer exists |
| 401 | `TOKEN_EXPIRED` | Token is past its expiry |
| 401 | `LOGIN_FAILED` | Wrong username or password |
| 403 | `PERMISSION_DENIED` | The user, or the root, lacks a required permission |
| 403 | `PATH_OUTSIDE_ROOTS` | The resolved path is not inside any root the user can access |
| 403 | `SYSTEM_ACCESS_DENIED` | The OS refused the operation (file permissions, etc.) |
| 404 | `NOT_FOUND` | File or folder does not exist |
| 409 | `ALREADY_EXISTS` | Target exists and `overwrite` is `false` |
| 409 | `DIRECTORY_NOT_EMPTY` | Deleting a non-empty folder without `recursive: true` |
| 405 | *(no JSON body)* | Wrong HTTP method for an existing endpoint |
| 416 | *(no JSON body)* | `Range` header can't be satisfied (download only) |
| 415 | `THUMBNAIL_UNAVAILABLE` | No preview can be made: not a supported image, too big, damaged, or thumbnails are turned off |
| 429 | `TOO_MANY_ATTEMPTS` | Login rate limit. `details.retry_after_seconds` is set, and so is the `Retry-After` header |
| 500 | `TRASH_FAILED` | Moving to trash failed (e.g. no trash on this system). Retry with `permanent: true` |
| 500 | `INTERNAL` | Anything unexpected. Details are logged on the server only |

---

## Paths, roots and permissions

### Paths

- Every path sent to the server must be **absolute**, e.g. `/home/me/docs/a.txt` or `C:\Users\me\a.txt`.
  On Windows, `/` and `\` are both accepted.
- Relative paths are rejected with `400 INVALID_PATH`.
- When the server receives a path, it immediately converts it to a **canonical absolute path**:
  `.` and `..` are resolved and symlinks are resolved. Every later check uses that canonical path, and paths
  in responses are always canonical, so they may differ from what was sent.
- Paths that don't exist yet (destinations for create, copy, move) are resolved through their deepest
  existing parent folder.
- **Symlinks:**
  - Reading through a symlink (`list`, `download`, `stat` with `follow=true`) follows it. If the link points
    outside every accessible root, the request fails with `403 PATH_OUTSIDE_ROOTS`. Links like that still
    appear in listings.
  - `delete` and `move` (and `stat` by default) act on **the link itself**, never on its target.

### Roots

A **root** is a top-level folder the server exposes. Every path must be inside a root. There are two kinds:

- `mount`: configured in `[[roots.mounts]]`.
- `drive`: every disk mount point. These appear only when `roots.expose_drives = true`, and they use
  `roots.drive_permissions`. The disk list is refreshed on every `GET /roots` call, so newly attached
  drives show up without a restart.

If roots are nested (for example a mount inside a drive), the **deepest** root that contains the path is the
one that applies.

### Permissions

There are three permissions: `read`, `write` and `delete`.

The permissions that apply to a request are **the user's permissions ∩ the root's permissions**. A root the
user has no `read` on is hidden from `GET /roots` and inaccessible.

| Endpoint | Required |
|---|---|
| `roots` | none (only roots with `read` are returned) |
| `stat`, `list`, `search`, `download`, `thumbnail` | `read` |
| `file`, `dir` | `write` on the parent's root |
| `copy` | `read` on the source root, `write` on the destination root |
| `move` (same parent folder, i.e. rename) | `write` |
| `move` (different folder) | `write` + `delete` on the source root, `write` on the destination root |
| `delete` | `delete` |

---

## Common objects

### Entry

Describes a file, folder, symlink or other filesystem object.

```json
{
  "name": "notes.txt",
  "path": "/home/me/Documents/notes.txt",
  "kind": "file",
  "size": 1234,
  "modified": 1759750000,
  "mime_type": "text/plain",
  "hidden": false,
  "symlink_target": null,
  "target_kind": null
}
```

| Field | Type | Notes |
|---|---|---|
| `name` | string | Final path component. Non-UTF-8 names are converted lossily |
| `path` | string | Canonical absolute path. Send this back for follow-up requests |
| `kind` | `"file"` \| `"dir"` \| `"symlink"` \| `"other"` | `other` = sockets, pipes, devices |
| `size` | integer | Bytes for files, `0` otherwise |
| `modified` | integer \| null | Last modified time. `null` if the OS doesn't provide it |
| `mime_type` | string \| null | Guessed from the extension. Files only |
| `hidden` | bool | Name starts with `.`, or has the hidden attribute on Windows |
| `symlink_target` | string \| null | Raw link target (symlinks only) |
| `target_kind` | `"file"` \| `"dir"` \| `"other"` \| null | What the symlink points to. `null` if it isn't a symlink or the link is broken |

### Root

```json
{
  "name": "Home",
  "path": "/home/me",
  "kind": "mount",
  "permissions": ["read", "write", "delete"],
  "disk": {
    "name": "/dev/nvme0n1p2",
    "mount_point": "/",
    "fs_type": "ext4",
    "disk_kind": "ssd",
    "removable": false,
    "total_bytes": 512000000000,
    "available_bytes": 210000000000
  }
}
```

| Field | Notes |
|---|---|
| `kind` | `"mount"` or `"drive"` |
| `permissions` | Effective permissions for **this user** on this root |
| `disk` | The disk the root lives on, or `null` if unknown. `disk_kind`: `"hdd"` \| `"ssd"` \| `"unknown"` |

---

## Endpoints

### Public

#### `GET /`

Plain-text liveness check. Returns `LunarLink <server_version>`.

#### `GET /api/info`

Basic server details shown before connecting. No auth required.

```json
{
  "name": "my-desktop",
  "os_type": "linux",
  "os_version": "rolling",
  "server_version": "0.2.0",
  "api_version": 1,
  "auth_required": true,
  "features": ["search", "thumbnails"]
}
```

- `name` is `server.display_name` if set, otherwise the machine hostname. It is never the OS user name.
- `os_type` is one of `linux`, `windows`, `macos`, etc.
- `features` lists optional capabilities, so clients can hide what an older server doesn't support.
  Unknown names should be ignored. Current values: `search` (`GET /api/v1/fs/search`) and
  `thumbnails` (`GET /api/v1/fs/thumbnail`, absent when `thumbnails.enabled = false`).

### Auth

#### `POST /api/auth/login`

Request:

```json
{ "username": "power", "password": "power" }
```

Response `200`:

```json
{
  "token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "expires_at": 1759757200,
  "user": { "username": "power", "permissions": ["read", "write", "delete"] }
}
```

Errors: `400 BAD_REQUEST`, `401 LOGIN_FAILED`, `429 TOO_MANY_ATTEMPTS`.

#### `GET /api/auth/me`  🔒

Returns the current user and their permissions from the current config.

```json
{ "username": "power", "permissions": ["read", "write", "delete"] }
```

### Filesystem

All filesystem endpoints are under `/api/v1/fs` and require auth (🔒).
Read operations are `GET` with query parameters. Changes are `POST` with a JSON body.

#### `GET /api/v1/fs/roots`  🔒

Every root the current user can read.

```json
{ "roots": [ /* Root */ ] }
```

#### `GET /api/v1/fs/stat?path=<path>&follow=<bool>`  🔒

Returns the [Entry](#entry) for one path.

| Query | Default | Notes |
|---|---|---|
| `path` | required | |
| `follow` | `false` | `true` describes the symlink's target instead of the link |

Errors: `NOT_FOUND`, `PATH_OUTSIDE_ROOTS`, `PERMISSION_DENIED`.

#### `GET /api/v1/fs/list?path=<dir>`  🔒

Lists a folder's contents.

| Query | Default | Notes |
|---|---|---|
| `path` | required | Must be a folder |
| `offset` | `0` | Entries to skip |
| `limit` | *(all)* | Maximum entries to return |
| `sort` | `name` | `name` \| `modified` \| `size` \| `kind` |
| `order` | `asc` | `asc` \| `desc` |
| `dirs_first` | `true` | Folders (and symlinks to folders) before files |
| `show_hidden` | `true` | `false` excludes hidden entries, and they are not counted in `total` |

Name sorting is case-insensitive. Entries the server can't read are skipped (and logged).

Response `200`:

```json
{
  "entry": { /* Entry for the folder itself */ },
  "items": [ /* Entry */ ],
  "total": 240,
  "offset": 0,
  "limit": 100
}
```

`total` counts every entry after the `show_hidden` filter, not just this page. `limit` is `null` when it
wasn't given.

Errors: `NOT_FOUND`, `NOT_A_DIRECTORY`, `PATH_OUTSIDE_ROOTS`, `PERMISSION_DENIED`.

#### `GET /api/v1/fs/search?path=<dir>&query=<name>`  🔒

Finds files and folders below `path` (at any depth) whose name contains `query`, ignoring case.

| Query | Default | Notes |
|---|---|---|
| `path` | required | Folder to search in. Must be a folder |
| `query` | required | Text to look for in names. Leading and trailing spaces are ignored; empty is `400 BAD_REQUEST` |
| `limit` | `200` | Maximum results, clamped to `1..=1000` |
| `show_hidden` | `true` | `false` skips hidden entries and does not search inside hidden folders |

- The search is breadth-first: matches closer to `path` come first. Within a folder, names are in
  natural order.
- Symlinks are listed when their name matches but are never followed.
- Nested roots the user can't read are not searched.
- The search stops after `limit` results or about 10 seconds, whichever comes first, and then sets
  `truncated: true`. It also stops when the client disconnects.

Response `200`:

```json
{ "items": [ /* Entry */ ], "truncated": false }
```

Errors: `BAD_REQUEST`, `NOT_FOUND`, `NOT_A_DIRECTORY`, `PATH_OUTSIDE_ROOTS`, `PERMISSION_DENIED`.

#### `GET /api/v1/fs/download?path=<file>`  🔒

Streams the file's bytes and supports HTTP `Range` requests.

- Without `Range`, the response is `200` with the whole file.
- With `Range: bytes=<start>-<end>`, the response is `206 Partial Content` with `Content-Range`.
- A range that can't be satisfied returns `416`.
- Response headers: `Content-Type` (guessed, default `application/octet-stream`), `Content-Length`,
  `Accept-Ranges: bytes`, `Last-Modified`, `Content-Disposition: attachment; filename*=UTF-8''<name>`.

Errors (JSON): `NOT_FOUND`, `IS_A_DIRECTORY`, `PATH_OUTSIDE_ROOTS`, `PERMISSION_DENIED`, `SYSTEM_ACCESS_DENIED`.

#### `GET /api/v1/fs/thumbnail?path=<file>&size=<px>`  🔒

A small preview of an image that fits in `size`×`size` pixels, keeping its aspect ratio. Images that
are already smaller are not enlarged.

| Query | Default | Notes |
|---|---|---|
| `path` | required | An image file (JPEG, PNG, GIF, WebP or BMP) |
| `size` | `256` | Clamped to `64..=512` |

- The response body is the image: `image/jpeg`, or `image/png` when the image has transparency.
  `Cache-Control: private, max-age=604800`.
- EXIF orientation is applied, so phone photos come out upright.
- Previews are cached on disk (`thumbnails.cache_dir`), keyed by path, modification time, file size
  and `size`, so a changed file gets a new preview. Cached previews are removed after 30 days.
- At most 4 previews are made at the same time; further requests wait.

Errors (JSON): `THUMBNAIL_UNAVAILABLE`, `NOT_FOUND`, `IS_A_DIRECTORY`, `PATH_OUTSIDE_ROOTS`,
`PERMISSION_DENIED`.

#### `POST /api/v1/fs/file`  🔒

Creates a new empty file. The parent folder must exist.

```json
{ "path": "/home/me/new.txt" }
```

Response `201`: the [Entry](#entry) of the new file. If the file already exists, the response is
`409 ALREADY_EXISTS`.

#### `POST /api/v1/fs/dir`  🔒

Creates a folder.

```json
{ "path": "/home/me/a/b/c", "parents": true }
```

| Field | Default | Notes |
|---|---|---|
| `path` | required | |
| `parents` | `false` | `true` also creates missing parent folders (like `mkdir -p`) |

Response `201`: the [Entry](#entry) of the new folder. If it already exists, the response is
`409 ALREADY_EXISTS`.

#### `POST /api/v1/fs/copy`  🔒

Copies a file or a whole folder.

```json
{ "from": "/home/me/a", "to": "/mnt/backup/a", "overwrite": false }
```

- `to` is the **full destination path**, not the folder to copy into.
- Folders are copied recursively. Symlinks inside are copied as symlinks, not followed.
- If `to` exists: with `overwrite: false` the response is `409 ALREADY_EXISTS`. With `overwrite: true` the
  existing target is **replaced** (for a folder, the old folder is removed first, not merged).
- Copying a folder into itself, or onto a folder that contains the source, returns `400 INVALID_OPERATION`.
- A root can't be the destination (`400 INVALID_OPERATION`).

Response `201`: the [Entry](#entry) at `to`.

#### `POST /api/v1/fs/move`  🔒

Moves or renames a file, folder or symlink. Renaming is a move within the same folder.

```json
{ "from": "/home/me/old.txt", "to": "/home/me/new.txt", "overwrite": false }
```

- Moving between disks works: the server copies, then deletes the original.
- `overwrite` behaves as it does for `copy`.
- Moving a folder into itself, moving a root, or replacing a root returns `400 INVALID_OPERATION`.
- Moving a path to itself does nothing and returns its entry.

Response `200`: the [Entry](#entry) at `to`.

#### `POST /api/v1/fs/delete`  🔒

```json
{ "path": "/home/me/old", "permanent": false, "recursive": false }
```

| Field | Default | Notes |
|---|---|---|
| `permanent` | `false` | `false` moves the item to the system trash. `true` deletes it for good |
| `recursive` | `false` | Must be `true` to delete a non-empty folder. Otherwise the response is `409 DIRECTORY_NOT_EMPTY` |

- A symlink is removed itself. Its target is never touched.
- A root can't be deleted (`400 INVALID_OPERATION`).

Response `204`, no body.

---

## Other behaviour

- **CORS:** any origin. Allowed methods: `GET`, `POST`, `OPTIONS`. Allowed headers: `Authorization`,
  `Content-Type`, `Range`. Exposed headers: `Content-Range`, `Accept-Ranges`, `Content-Disposition`,
  `Retry-After`.
- **Concurrency:** at most `server.max_concurrent_requests` requests are handled at the same time. Extra
  requests wait in a queue; they are not rejected. A request counts until its response headers are sent,
  so a download in progress no longer counts once its body is streaming.
- **Request validation:** unknown fields in JSON bodies and query strings are rejected with
  `400 BAD_REQUEST`, so typos don't silently do nothing.
- **Logging:** controlled with `RUST_LOG` (default `info`), e.g. `RUST_LOG=lunar_lib=debug`.

---

## Configuration reference

Run the server as `lunar_link [config_path]`. The default path is `config.toml` in the working directory.

- If the file is missing, a default one is created with a random `jwt_secret` and a user `admin` with a
  random password. The password is printed once at startup.
- Unknown keys are rejected, so typos and settings from older versions fail loudly instead of being
  ignored. A config in the old format fails to load with a message explaining how to regenerate it.
- `config.example.toml` shows every option.

```toml
[server]
host = "0.0.0.0"
port = 1234
max_concurrent_requests = 100
# display_name = "My Desktop"   # optional. Defaults to the hostname
show_qr = false                 # print a QR code with the server URL at startup

[auth]
enabled = true
token_ttl_minutes = 120
jwt_secret = "<random, generated on first run>"

# Passwords are currently plain text. Hashed passwords are planned for a later release.
[[users]]
username = "power"
password = "power"
permissions = ["read", "write", "delete"]   # required. [] = can log in but access nothing

[thumbnails]
enabled = true                     # previews of images for clients
max_source_mb = 64                 # images bigger than this are not previewed
# cache_dir = "~/.cache/lunarlink/thumbs"   # defaults to the OS cache folder

[roots]
expose_drives = false              # add every disk mount point as a root
drive_permissions = ["read"]       # permissions for drive roots

[[roots.mounts]]
name = "Home"
path = "~"                         # ~, $VAR, ${VAR} and %VAR% are expanded
permissions = ["read", "write", "delete"]   # if omitted: ["read"]. [] = hidden and inaccessible
```

A mount whose path doesn't exist is skipped with a warning at startup.

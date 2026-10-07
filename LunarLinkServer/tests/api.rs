//! End-to-end tests of the HTTP API against temporary folders.

use axum::body::Body;
use axum::http::{header, HeaderMap, Method, Request, StatusCode};
use axum::Router;
use http_body_util::BodyExt;
use lunar_lib::auth::jwt::{encode_claims, Claims};
use lunar_lib::config::LunarConfig;
use lunar_lib::server::build_app;
use lunar_lib::server::state::AppState;
use serde_json::{json, Value};
use std::fs;
use std::path::{Path, PathBuf};
use tower::ServiceExt;

const SECRET: &str = "integration-test-secret-0123456789abcdef";

struct TestServer {
    _dir: tempfile::TempDir,
    /// Canonical temp folder containing `share/`, `readonly/` and `outside.txt`.
    base: PathBuf,
    app: Router,
}

struct Response {
    status: StatusCode,
    headers: HeaderMap,
    body: Vec<u8>,
}

impl Response {
    fn json(&self) -> Value {
        serde_json::from_slice(&self.body).unwrap_or_else(|_| {
            panic!(
                "not JSON (status {}): {}",
                self.status,
                String::from_utf8_lossy(&self.body)
            )
        })
    }

    fn error_code(&self) -> String {
        self.json()["error"]["code"].as_str().unwrap().to_string()
    }
}

fn server(auth_enabled: bool) -> TestServer {
    let dir = tempfile::tempdir().unwrap();
    let base = dunce::canonicalize(dir.path()).unwrap();
    fs::create_dir_all(base.join("share")).unwrap();
    fs::create_dir_all(base.join("readonly")).unwrap();
    fs::write(base.join("readonly/info.txt"), "read me").unwrap();
    fs::write(base.join("outside.txt"), "secret").unwrap();

    let toml = format!(
        r#"
[auth]
enabled = {auth_enabled}
jwt_secret = "{SECRET}"

[[users]]
username = "admin"
password = "admin-pass"
permissions = ["read", "write", "delete"]

[[users]]
username = "reader"
password = "reader-pass"
permissions = ["read"]

[[users]]
username = "nobody"
password = "nobody-pass"
permissions = []

[thumbnails]
cache_dir = "{cache}"

[[roots.mounts]]
name = "share"
path = "{share}"
permissions = ["read", "write", "delete"]

[[roots.mounts]]
name = "readonly"
path = "{readonly}"
"#,
        share = base.join("share").display(),
        readonly = base.join("readonly").display(),
        cache = base.join("thumb-cache").display(),
    );
    let config = LunarConfig::from_toml_str(&toml).unwrap();
    TestServer {
        _dir: dir,
        base,
        app: build_app(AppState::new(config)),
    }
}

fn enc(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

fn p(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

impl TestServer {
    async fn send(&self, req: Request<Body>) -> Response {
        let res = self.app.clone().oneshot(req).await.unwrap();
        let status = res.status();
        let headers = res.headers().clone();
        let body = res.into_body().collect().await.unwrap().to_bytes().to_vec();
        Response {
            status,
            headers,
            body,
        }
    }

    async fn get(&self, uri: &str, token: Option<&str>) -> Response {
        let mut req = Request::get(uri);
        if let Some(t) = token {
            req = req.header(header::AUTHORIZATION, format!("Bearer {t}"));
        }
        self.send(req.body(Body::empty()).unwrap()).await
    }

    async fn post(&self, uri: &str, token: Option<&str>, body: Value) -> Response {
        let mut req = Request::post(uri).header(header::CONTENT_TYPE, "application/json");
        if let Some(t) = token {
            req = req.header(header::AUTHORIZATION, format!("Bearer {t}"));
        }
        self.send(req.body(Body::from(body.to_string())).unwrap())
            .await
    }

    async fn login(&self, user: &str, pass: &str) -> Response {
        self.post(
            "/api/auth/login",
            None,
            json!({ "username": user, "password": pass }),
        )
        .await
    }

    async fn token(&self, user: &str) -> String {
        let res = self.login(user, &format!("{user}-pass")).await;
        assert_eq!(res.status, StatusCode::OK);
        res.json()["token"].as_str().unwrap().to_string()
    }

    async fn stat(&self, path: &Path, token: &str) -> Response {
        self.get(
            &format!("/api/v1/fs/stat?path={}", enc(&p(path))),
            Some(token),
        )
        .await
    }

    fn share(&self) -> PathBuf {
        self.base.join("share")
    }
}

// ---------- public endpoints ----------

#[tokio::test]
async fn root_info_and_unknown_routes() {
    let s = server(true);
    let res = s.get("/", None).await;
    assert_eq!(res.status, StatusCode::OK);
    assert!(String::from_utf8(res.body)
        .unwrap()
        .starts_with("LunarLink "));

    let info = s.get("/api/info", None).await.json();
    assert_eq!(info["api_version"], 1);
    assert_eq!(info["auth_required"], true);
    assert!(info["features"]
        .as_array()
        .unwrap()
        .contains(&json!("search")));
    assert!(info["features"]
        .as_array()
        .unwrap()
        .contains(&json!("thumbnails")));
    assert_eq!(info["os_type"], std::env::consts::OS);
    assert!(info["name"].is_string());
    assert!(info.get("device_name").is_none());

    let res = s.get("/api/nope", None).await;
    assert_eq!(res.status, StatusCode::NOT_FOUND);
    assert_eq!(res.error_code(), "NOT_FOUND");
}

#[tokio::test]
async fn cors_preflight_is_answered() {
    let s = server(true);
    let req = Request::builder()
        .method(Method::OPTIONS)
        .uri("/api/v1/fs/list")
        .header(header::ORIGIN, "http://example.com")
        .header(header::ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(header::ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
        .body(Body::empty())
        .unwrap();
    let res = s.send(req).await;
    assert!(res.status.is_success());
    assert_eq!(res.headers[header::ACCESS_CONTROL_ALLOW_ORIGIN], "*");
}

// ---------- auth ----------

#[tokio::test]
async fn login_and_me() {
    let s = server(true);
    let res = s.login("admin", "admin-pass").await;
    assert_eq!(res.status, StatusCode::OK);
    let body = res.json();
    assert_eq!(body["token_type"], "Bearer");
    assert_eq!(body["user"]["username"], "admin");
    assert_eq!(
        body["user"]["permissions"],
        json!(["read", "write", "delete"])
    );
    assert!(body["expires_at"].as_u64().unwrap() > 0);

    let token = body["token"].as_str().unwrap();
    let me = s.get("/api/auth/me", Some(token)).await.json();
    assert_eq!(
        me,
        json!({ "username": "admin", "permissions": ["read", "write", "delete"] })
    );
}

#[tokio::test]
async fn login_failures_are_rate_limited() {
    let s = server(true);
    for _ in 0..5 {
        let res = s.login("admin", "wrong").await;
        assert_eq!(res.status, StatusCode::UNAUTHORIZED);
        assert_eq!(res.error_code(), "LOGIN_FAILED");
    }
    // Locked now, even with the right password.
    let res = s.login("admin", "admin-pass").await;
    assert_eq!(res.status, StatusCode::TOO_MANY_REQUESTS);
    assert_eq!(res.error_code(), "TOO_MANY_ATTEMPTS");
    assert!(res.headers.contains_key(header::RETRY_AFTER));
    assert!(
        res.json()["error"]["details"]["retry_after_seconds"]
            .as_u64()
            .unwrap()
            >= 1
    );

    // Other users are not affected; unknown users just fail.
    assert_eq!(
        s.login("reader", "reader-pass").await.status,
        StatusCode::OK
    );
    assert_eq!(s.login("ghost", "x").await.error_code(), "LOGIN_FAILED");
}

#[tokio::test]
async fn login_rejects_bad_bodies() {
    let s = server(true);
    let res = s
        .post("/api/auth/login", None, json!({ "username": "admin" }))
        .await;
    assert_eq!(res.status, StatusCode::BAD_REQUEST);
    assert_eq!(res.error_code(), "BAD_REQUEST");

    let req = Request::post("/api/auth/login")
        .body(Body::from("username=admin"))
        .unwrap();
    assert_eq!(s.send(req).await.error_code(), "BAD_REQUEST");
}

#[tokio::test]
async fn authorization_header_rules() {
    let s = server(true);
    let uri = "/api/auth/me";

    let res = s.get(uri, None).await;
    assert_eq!(res.status, StatusCode::UNAUTHORIZED);
    assert_eq!(res.error_code(), "UNAUTHENTICATED");

    let token = s.token("admin").await;
    let with = |value: String| {
        Request::get(uri)
            .header(header::AUTHORIZATION, value)
            .body(Body::empty())
            .unwrap()
    };
    assert_eq!(
        s.send(with(format!("Garbage {token}"))).await.error_code(),
        "INVALID_TOKEN"
    );
    assert_eq!(
        s.send(with(token.clone())).await.error_code(),
        "INVALID_TOKEN"
    );
    assert_eq!(
        s.send(with(format!("bearer {token}"))).await.status,
        StatusCode::OK
    );
    assert_eq!(
        s.send(with("Bearer abc.def.ghi".into())).await.error_code(),
        "INVALID_TOKEN"
    );

    let now = chrono::Utc::now().timestamp() as u64;
    let expired = encode_claims(
        &Claims {
            sub: "admin".into(),
            iat: now - 100,
            exp: now - 10,
        },
        SECRET.as_bytes(),
    )
    .unwrap();
    assert_eq!(
        s.get(uri, Some(&expired)).await.error_code(),
        "TOKEN_EXPIRED"
    );

    let ghost = encode_claims(
        &Claims {
            sub: "ghost".into(),
            iat: now,
            exp: now + 60,
        },
        SECRET.as_bytes(),
    )
    .unwrap();
    assert_eq!(s.get(uri, Some(&ghost)).await.error_code(), "INVALID_TOKEN");
}

#[tokio::test]
async fn auth_disabled_runs_as_anonymous_within_root_permissions() {
    let s = server(false);
    assert_eq!(
        s.get("/api/info", None).await.json()["auth_required"],
        false
    );
    let me = s.get("/api/auth/me", None).await.json();
    assert_eq!(me["username"], "anonymous");

    let res = s
        .post(
            "/api/v1/fs/file",
            None,
            json!({ "path": p(&s.share().join("a.txt")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::CREATED);

    let res = s
        .post(
            "/api/v1/fs/file",
            None,
            json!({ "path": p(&s.base.join("readonly/a.txt")) }),
        )
        .await;
    assert_eq!(res.error_code(), "PERMISSION_DENIED");

    // A real token still works and is validated.
    let token = s.token("reader").await;
    assert_eq!(
        s.get("/api/auth/me", Some(&token)).await.json()["username"],
        "reader"
    );
}

// ---------- roots ----------

#[tokio::test]
async fn roots_show_effective_permissions() {
    let s = server(true);
    let roots = |v: Value| -> Vec<(String, Value)> {
        v["roots"]
            .as_array()
            .unwrap()
            .iter()
            .map(|r| {
                (
                    r["name"].as_str().unwrap().to_string(),
                    r["permissions"].clone(),
                )
            })
            .collect()
    };

    let admin = s.token("admin").await;
    let res = s.get("/api/v1/fs/roots", Some(&admin)).await.json();
    assert_eq!(
        roots(res.clone()),
        vec![
            ("share".to_string(), json!(["read", "write", "delete"])),
            ("readonly".to_string(), json!(["read"])),
        ]
    );
    assert_eq!(res["roots"][0]["kind"], "mount");
    assert_eq!(res["roots"][0]["path"], p(&s.share()));

    let reader = s.token("reader").await;
    let res = s.get("/api/v1/fs/roots", Some(&reader)).await.json();
    assert_eq!(roots(res)[0].1, json!(["read"]));

    let nobody = s.token("nobody").await;
    let res = s.get("/api/v1/fs/roots", Some(&nobody)).await.json();
    assert!(res["roots"].as_array().unwrap().is_empty());
}

// ---------- sandboxing ----------

#[tokio::test]
async fn paths_outside_roots_are_rejected() {
    let s = server(true);
    let token = s.token("admin").await;

    for path in [
        "/etc/passwd".to_string(),
        p(&s.base.join("outside.txt")),
        format!("{}/../outside.txt", p(&s.share())),
        p(&s.base),
    ] {
        let res = s
            .get(
                &format!("/api/v1/fs/download?path={}", enc(&path)),
                Some(&token),
            )
            .await;
        assert_eq!(res.status, StatusCode::FORBIDDEN, "{path}");
        assert_eq!(res.error_code(), "PATH_OUTSIDE_ROOTS", "{path}");
    }

    let res = s
        .post(
            "/api/v1/fs/delete",
            Some(&token),
            json!({ "path": p(&s.base.join("outside.txt")), "permanent": true }),
        )
        .await;
    assert_eq!(res.error_code(), "PATH_OUTSIDE_ROOTS");
    assert!(s.base.join("outside.txt").exists());

    let res = s.get("/api/v1/fs/stat?path=relative/x", Some(&token)).await;
    assert_eq!(res.status, StatusCode::BAD_REQUEST);
    assert_eq!(res.error_code(), "INVALID_PATH");

    let res = s.get("/api/v1/fs/stat", Some(&token)).await;
    assert_eq!(res.error_code(), "BAD_REQUEST");
}

#[cfg(unix)]
#[tokio::test]
async fn symlinks_cannot_escape_roots() {
    use std::os::unix::fs::symlink;
    let s = server(true);
    let token = s.token("admin").await;
    symlink(s.base.join("outside.txt"), s.share().join("escape")).unwrap();
    symlink(&s.base, s.share().join("escape_dir")).unwrap();

    let link = s.share().join("escape");
    let res = s
        .get(
            &format!("/api/v1/fs/download?path={}", enc(&p(&link))),
            Some(&token),
        )
        .await;
    assert_eq!(res.error_code(), "PATH_OUTSIDE_ROOTS");
    let res = s
        .get(
            &format!(
                "/api/v1/fs/list?path={}",
                enc(&p(&s.share().join("escape_dir")))
            ),
            Some(&token),
        )
        .await;
    assert_eq!(res.error_code(), "PATH_OUTSIDE_ROOTS");

    // The link itself is visible and can be described and deleted.
    let entry = s.stat(&link, &token).await.json();
    assert_eq!(entry["kind"], "symlink");
    assert_eq!(entry["target_kind"], "file");
    let res = s
        .get(
            &format!("/api/v1/fs/stat?follow=true&path={}", enc(&p(&link))),
            Some(&token),
        )
        .await;
    assert_eq!(res.error_code(), "PATH_OUTSIDE_ROOTS");

    let res = s
        .post(
            "/api/v1/fs/delete",
            Some(&token),
            json!({ "path": p(&link), "permanent": true }),
        )
        .await;
    assert_eq!(res.status, StatusCode::NO_CONTENT);
    assert!(s.base.join("outside.txt").exists());
}

// ---------- reading ----------

#[tokio::test]
async fn list_sorts_filters_and_paginates() {
    let s = server(true);
    let token = s.token("reader").await;
    let share = s.share();
    fs::write(share.join("b.txt"), "12345").unwrap();
    fs::write(share.join("A.txt"), "1").unwrap();
    fs::write(share.join(".hidden"), "").unwrap();
    fs::create_dir(share.join("dir")).unwrap();

    let base = format!("/api/v1/fs/list?path={}", enc(&p(&share)));
    let res = s.get(&base, Some(&token)).await.json();
    let names: Vec<&str> = res["items"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e["name"].as_str().unwrap())
        .collect();
    assert_eq!(names, ["dir", ".hidden", "A.txt", "b.txt"]);
    assert_eq!(res["total"], 4);
    assert_eq!(res["entry"]["kind"], "dir");
    assert_eq!(res["limit"], Value::Null);
    assert_eq!(res["items"][3]["mime_type"], "text/plain");
    assert_eq!(res["items"][3]["path"], p(&share.join("b.txt")));

    let res = s
        .get(
            &format!("{base}&show_hidden=false&offset=1&limit=1&sort=size&order=desc"),
            Some(&token),
        )
        .await
        .json();
    assert_eq!(res["total"], 3);
    assert_eq!(res["items"].as_array().unwrap().len(), 1);
    assert_eq!(res["items"][0]["name"], "b.txt");

    let res = s.get(&format!("{base}&sort=bogus"), Some(&token)).await;
    assert_eq!(res.error_code(), "BAD_REQUEST");

    let file = format!("/api/v1/fs/list?path={}", enc(&p(&share.join("b.txt"))));
    assert_eq!(
        s.get(&file, Some(&token)).await.error_code(),
        "NOT_A_DIRECTORY"
    );
    let missing = format!("/api/v1/fs/list?path={}", enc(&p(&share.join("nope"))));
    assert_eq!(
        s.get(&missing, Some(&token)).await.status,
        StatusCode::NOT_FOUND
    );
}

#[tokio::test]
async fn download_full_range_and_errors() {
    let s = server(true);
    let token = s.token("reader").await;
    let file = s.share().join("data file.txt");
    fs::write(&file, "0123456789").unwrap();
    let uri = format!("/api/v1/fs/download?path={}", enc(&p(&file)));

    let res = s.get(&uri, Some(&token)).await;
    assert_eq!(res.status, StatusCode::OK);
    assert_eq!(res.body, b"0123456789");
    assert_eq!(res.headers[header::CONTENT_TYPE], "text/plain");
    assert_eq!(res.headers[header::CONTENT_LENGTH], "10");
    assert_eq!(res.headers[header::ACCEPT_RANGES], "bytes");
    assert!(res.headers.contains_key(header::LAST_MODIFIED));
    assert_eq!(
        res.headers[header::CONTENT_DISPOSITION],
        "attachment; filename*=UTF-8''data%20file.txt"
    );

    let ranged = |range: &'static str| {
        Request::get(&uri)
            .header(header::AUTHORIZATION, format!("Bearer {token}"))
            .header(header::RANGE, range)
            .body(Body::empty())
            .unwrap()
    };
    let res = s.send(ranged("bytes=2-5")).await;
    assert_eq!(res.status, StatusCode::PARTIAL_CONTENT);
    assert_eq!(res.body, b"2345");
    assert_eq!(res.headers[header::CONTENT_RANGE], "bytes 2-5/10");

    let res = s.send(ranged("bytes=50-60")).await;
    assert_eq!(res.status, StatusCode::RANGE_NOT_SATISFIABLE);

    let dir = format!("/api/v1/fs/download?path={}", enc(&p(&s.share())));
    assert_eq!(
        s.get(&dir, Some(&token)).await.error_code(),
        "IS_A_DIRECTORY"
    );
    let missing = format!("/api/v1/fs/download?path={}", enc(&p(&s.share().join("x"))));
    assert_eq!(
        s.get(&missing, Some(&token)).await.error_code(),
        "NOT_FOUND"
    );
}

// ---------- writing ----------

#[tokio::test]
async fn create_file_and_dir() {
    let s = server(true);
    let token = s.token("admin").await;
    let file = s.share().join("new.txt");

    let res = s
        .post("/api/v1/fs/file", Some(&token), json!({ "path": p(&file) }))
        .await;
    assert_eq!(res.status, StatusCode::CREATED);
    assert_eq!(res.json()["kind"], "file");
    let res = s
        .post("/api/v1/fs/file", Some(&token), json!({ "path": p(&file) }))
        .await;
    assert_eq!(res.status, StatusCode::CONFLICT);
    assert_eq!(res.error_code(), "ALREADY_EXISTS");

    let nested = s.share().join("a/b/c");
    let res = s
        .post(
            "/api/v1/fs/dir",
            Some(&token),
            json!({ "path": p(&nested) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::NOT_FOUND);
    let res = s
        .post(
            "/api/v1/fs/dir",
            Some(&token),
            json!({ "path": p(&nested), "parents": true }),
        )
        .await;
    assert_eq!(res.status, StatusCode::CREATED);
    assert_eq!(res.json()["path"], p(&nested));
    let res = s
        .post(
            "/api/v1/fs/dir",
            Some(&token),
            json!({ "path": p(&nested), "parents": true }),
        )
        .await;
    assert_eq!(res.error_code(), "ALREADY_EXISTS");

    let res = s
        .post(
            "/api/v1/fs/dir",
            Some(&token),
            json!({ "path": p(&nested), "bogus": 1 }),
        )
        .await;
    assert_eq!(res.error_code(), "BAD_REQUEST");
}

#[tokio::test]
async fn copy_move_and_delete_flow() {
    let s = server(true);
    let token = s.token("admin").await;
    let share = s.share();
    fs::create_dir_all(share.join("folder/sub")).unwrap();
    fs::write(share.join("folder/sub/f.txt"), "content").unwrap();

    // copy a folder
    let res = s
        .post(
            "/api/v1/fs/copy",
            Some(&token),
            json!({ "from": p(&share.join("folder")), "to": p(&share.join("copy")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::CREATED);
    assert_eq!(res.json()["kind"], "dir");
    assert_eq!(
        fs::read_to_string(share.join("copy/sub/f.txt")).unwrap(),
        "content"
    );

    // conflict, then overwrite
    let body = json!({ "from": p(&share.join("folder")), "to": p(&share.join("copy")) });
    assert_eq!(
        s.post("/api/v1/fs/copy", Some(&token), body)
            .await
            .error_code(),
        "ALREADY_EXISTS"
    );
    let body = json!({ "from": p(&share.join("folder")), "to": p(&share.join("copy")), "overwrite": true });
    assert_eq!(
        s.post("/api/v1/fs/copy", Some(&token), body).await.status,
        StatusCode::CREATED
    );

    // into itself
    let body = json!({ "from": p(&share.join("folder")), "to": p(&share.join("folder/sub/x")) });
    assert_eq!(
        s.post("/api/v1/fs/copy", Some(&token), body)
            .await
            .error_code(),
        "INVALID_OPERATION"
    );

    // rename
    let res = s
        .post(
            "/api/v1/fs/move",
            Some(&token),
            json!({ "from": p(&share.join("copy")), "to": p(&share.join("renamed")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::OK);
    assert_eq!(res.json()["name"], "renamed");
    assert!(!share.join("copy").exists());

    // move to itself is a no-op
    let res = s
        .post(
            "/api/v1/fs/move",
            Some(&token),
            json!({ "from": p(&share.join("renamed")), "to": p(&share.join("renamed")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::OK);

    // delete needs recursive for non-empty folders
    let body = json!({ "path": p(&share.join("renamed")), "permanent": true });
    let res = s.post("/api/v1/fs/delete", Some(&token), body).await;
    assert_eq!(res.status, StatusCode::CONFLICT);
    assert_eq!(res.error_code(), "DIRECTORY_NOT_EMPTY");
    let body = json!({ "path": p(&share.join("renamed")), "permanent": true, "recursive": true });
    let res = s.post("/api/v1/fs/delete", Some(&token), body).await;
    assert_eq!(res.status, StatusCode::NO_CONTENT);
    assert!(res.body.is_empty());
    assert!(!share.join("renamed").exists());
}

#[tokio::test]
async fn roots_cannot_be_deleted_moved_or_replaced() {
    let s = server(true);
    let token = s.token("admin").await;
    let share = p(&s.share());
    fs::write(s.share().join("f.txt"), "x").unwrap();

    let res = s
        .post(
            "/api/v1/fs/delete",
            Some(&token),
            json!({ "path": share, "permanent": true, "recursive": true }),
        )
        .await;
    assert_eq!(res.error_code(), "INVALID_OPERATION");
    let res = s
        .post(
            "/api/v1/fs/move",
            Some(&token),
            json!({ "from": share, "to": format!("{share}-moved") }),
        )
        .await;
    assert_eq!(res.error_code(), "INVALID_OPERATION");
    let res = s
        .post(
            "/api/v1/fs/copy",
            Some(&token),
            json!({ "from": p(&s.share().join("f.txt")), "to": share, "overwrite": true }),
        )
        .await;
    assert_eq!(res.error_code(), "INVALID_OPERATION");
    assert!(s.share().join("f.txt").exists());
}

#[tokio::test]
async fn permissions_combine_user_and_root() {
    let s = server(true);
    let admin = s.token("admin").await;
    let reader = s.token("reader").await;
    let nobody = s.token("nobody").await;
    let share = s.share();
    let readonly = s.base.join("readonly");

    // reader: read only
    assert_eq!(
        s.stat(&readonly.join("info.txt"), &reader).await.status,
        StatusCode::OK
    );
    let res = s
        .post(
            "/api/v1/fs/file",
            Some(&reader),
            json!({ "path": p(&share.join("x")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::FORBIDDEN);
    assert_eq!(res.error_code(), "PERMISSION_DENIED");

    // nobody: nothing
    assert_eq!(
        s.stat(&share, &nobody).await.error_code(),
        "PERMISSION_DENIED"
    );

    // admin is limited by the read-only root
    let res = s
        .post(
            "/api/v1/fs/file",
            Some(&admin),
            json!({ "path": p(&readonly.join("x")) }),
        )
        .await;
    assert_eq!(res.error_code(), "PERMISSION_DENIED");
    let res = s
        .post(
            "/api/v1/fs/delete",
            Some(&admin),
            json!({ "path": p(&readonly.join("info.txt")), "permanent": true }),
        )
        .await;
    assert_eq!(res.error_code(), "PERMISSION_DENIED");

    // copy out of the read-only root works, moving out does not
    let body = json!({ "from": p(&readonly.join("info.txt")), "to": p(&share.join("info.txt")) });
    assert_eq!(
        s.post("/api/v1/fs/copy", Some(&admin), body).await.status,
        StatusCode::CREATED
    );
    let body = json!({ "from": p(&readonly.join("info.txt")), "to": p(&share.join("moved.txt")) });
    assert_eq!(
        s.post("/api/v1/fs/move", Some(&admin), body)
            .await
            .error_code(),
        "PERMISSION_DENIED"
    );
    assert!(readonly.join("info.txt").exists());
}

// ---------- search ----------

#[tokio::test]
async fn search_finds_nested_matches() {
    let s = server(true);
    let token = s.token("reader").await;
    let share = s.share();
    fs::create_dir_all(share.join("photos/2024")).unwrap();
    fs::write(share.join("Holiday.txt"), "").unwrap();
    fs::write(share.join("photos/2024/holiday-beach.jpg"), "").unwrap();
    fs::write(share.join(".holiday-draft"), "").unwrap();

    let base = format!("/api/v1/fs/search?path={}&query=HOLIDAY", enc(&p(&share)));
    let res = s.get(&base, Some(&token)).await;
    assert_eq!(res.status, StatusCode::OK);
    let body = res.json();
    let names: Vec<&str> = body["items"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e["name"].as_str().unwrap())
        .collect();
    assert_eq!(
        names,
        [".holiday-draft", "Holiday.txt", "holiday-beach.jpg"]
    );
    assert_eq!(body["truncated"], false);
    assert_eq!(
        body["items"][2]["path"],
        p(&share.join("photos/2024/holiday-beach.jpg"))
    );

    let res = s
        .get(&format!("{base}&show_hidden=false&limit=1"), Some(&token))
        .await
        .json();
    assert_eq!(res["items"].as_array().unwrap().len(), 1);
    assert_eq!(res["items"][0]["name"], "Holiday.txt");
    assert_eq!(res["truncated"], true);
}

#[tokio::test]
async fn search_errors() {
    let s = server(true);
    let token = s.token("admin").await;
    let share = s.share();
    fs::write(share.join("file.txt"), "").unwrap();
    let url = |path: &Path, q: &str| format!("/api/v1/fs/search?path={}&query={q}", enc(&p(path)));

    let res = s.get(&url(&share, "%20"), Some(&token)).await;
    assert_eq!(res.error_code(), "BAD_REQUEST");
    let res = s
        .get(&url(&share.join("file.txt"), "x"), Some(&token))
        .await;
    assert_eq!(res.error_code(), "NOT_A_DIRECTORY");
    let res = s.get(&url(&s.base, "outside"), Some(&token)).await;
    assert_eq!(res.error_code(), "PATH_OUTSIDE_ROOTS");
    let nobody = s.token("nobody").await;
    let res = s.get(&url(&share, "file"), Some(&nobody)).await;
    assert_eq!(res.error_code(), "PERMISSION_DENIED");
    let res = s.get(&url(&share, "file"), None).await;
    assert_eq!(res.error_code(), "UNAUTHENTICATED");
}

// ---------- thumbnails ----------

#[tokio::test]
async fn thumbnails_of_images() {
    let s = server(true);
    let token = s.token("reader").await;
    let share = s.share();
    image::RgbImage::from_pixel(640, 320, image::Rgb([200, 30, 30]))
        .save(share.join("photo.png"))
        .unwrap();
    fs::write(share.join("notes.txt"), "hi").unwrap();
    let url = |name: &str, extra: &str| {
        format!(
            "/api/v1/fs/thumbnail?path={}{extra}",
            enc(&p(&share.join(name)))
        )
    };

    let res = s.get(&url("photo.png", "&size=128"), Some(&token)).await;
    assert_eq!(res.status, StatusCode::OK);
    assert_eq!(res.headers[header::CONTENT_TYPE], "image/jpeg");
    assert!(res.headers[header::CACHE_CONTROL]
        .to_str()
        .unwrap()
        .contains("max-age"));
    let thumb = image::load_from_memory(&res.body).unwrap();
    assert_eq!((thumb.width(), thumb.height()), (128, 64));
    assert_eq!(fs::read_dir(s.base.join("thumb-cache")).unwrap().count(), 1);

    let res = s.get(&url("notes.txt", ""), Some(&token)).await;
    assert_eq!(res.status, StatusCode::UNSUPPORTED_MEDIA_TYPE);
    assert_eq!(res.error_code(), "THUMBNAIL_UNAVAILABLE");
    let res = s.get(&url("missing.png", ""), Some(&token)).await;
    assert_eq!(res.error_code(), "NOT_FOUND");
    let res = s.get(&url("photo.png", ""), None).await;
    assert_eq!(res.error_code(), "UNAUTHENTICATED");
    let nobody = s.token("nobody").await;
    let res = s.get(&url("photo.png", ""), Some(&nobody)).await;
    assert_eq!(res.error_code(), "PERMISSION_DENIED");
}

#[tokio::test]
async fn thumbnails_can_be_turned_off() {
    let dir = tempfile::tempdir().unwrap();
    let share = dunce::canonicalize(dir.path()).unwrap();
    let toml = format!(
        r#"
[auth]
enabled = false
jwt_secret = "{SECRET}"

[thumbnails]
enabled = false

[[roots.mounts]]
name = "share"
path = "{}"
"#,
        share.display()
    );
    let app = build_app(AppState::new(LunarConfig::from_toml_str(&toml).unwrap()));
    let s = TestServer {
        _dir: dir,
        base: share.clone(),
        app,
    };
    let info = s.get("/api/info", None).await.json();
    assert!(!info["features"]
        .as_array()
        .unwrap()
        .contains(&json!("thumbnails")));
    image::RgbImage::new(10, 10)
        .save(share.join("a.png"))
        .unwrap();
    let res = s
        .get(
            &format!(
                "/api/v1/fs/thumbnail?path={}",
                enc(&p(&share.join("a.png")))
            ),
            None,
        )
        .await;
    assert_eq!(res.error_code(), "THUMBNAIL_UNAVAILABLE");
}

// ---------- jobs ----------

impl TestServer {
    /// Polls a job until it finishes.
    async fn wait_job(&self, id: &str, token: &str) -> Value {
        for _ in 0..200 {
            let job = self
                .get(&format!("/api/v1/jobs/{id}"), Some(token))
                .await
                .json();
            if ["done", "failed", "cancelled"].contains(&job["state"].as_str().unwrap()) {
                return job;
            }
            tokio::time::sleep(std::time::Duration::from_millis(10)).await;
        }
        panic!("job {id} did not finish");
    }
}

#[tokio::test]
async fn copy_and_move_jobs() {
    let s = server(true);
    let token = s.token("admin").await;
    let share = s.share();
    fs::create_dir_all(share.join("src/sub")).unwrap();
    fs::write(share.join("src/a.txt"), "12345").unwrap();
    fs::write(share.join("src/sub/b.txt"), "123").unwrap();

    let res = s
        .post(
            "/api/v1/jobs/copy",
            Some(&token),
            json!({ "from": p(&share.join("src")), "to": p(&share.join("copy")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::ACCEPTED);
    let started = res.json();
    assert_eq!(started["kind"], "copy");
    let id = started["id"].as_str().unwrap().to_string();

    let done = s.wait_job(&id, &token).await;
    assert_eq!(done["state"], "done");
    assert_eq!(done["bytes_total"], 8);
    assert_eq!(done["bytes_done"], 8);
    assert_eq!(done["files_done"], 2);
    assert_eq!(done["entry"]["path"], p(&share.join("copy")));
    assert_eq!(done["error"], Value::Null);
    assert_eq!(
        fs::read_to_string(share.join("copy/sub/b.txt")).unwrap(),
        "123"
    );

    // Listed for the owner only.
    let jobs = s.get("/api/v1/jobs", Some(&token)).await.json();
    assert_eq!(jobs["jobs"].as_array().unwrap().len(), 1);
    let reader = s.token("reader").await;
    let theirs = s.get("/api/v1/jobs", Some(&reader)).await.json();
    assert!(theirs["jobs"].as_array().unwrap().is_empty());
    let res = s.get(&format!("/api/v1/jobs/{id}"), Some(&reader)).await;
    assert_eq!(res.error_code(), "NOT_FOUND");

    let res = s
        .post(
            "/api/v1/jobs/move",
            Some(&token),
            json!({ "from": p(&share.join("copy")), "to": p(&share.join("moved")) }),
        )
        .await;
    assert_eq!(res.status, StatusCode::ACCEPTED);
    let moved = s.wait_job(res.json()["id"].as_str().unwrap(), &token).await;
    assert_eq!(moved["state"], "done");
    assert!(!share.join("copy").exists());
    assert!(share.join("moved/a.txt").exists());
}

#[tokio::test]
async fn job_start_errors_are_synchronous() {
    let s = server(true);
    let token = s.token("admin").await;
    let share = s.share();
    fs::write(share.join("a.txt"), "a").unwrap();
    fs::write(share.join("b.txt"), "b").unwrap();
    let body =
        |from: &str, to: &str| json!({ "from": p(&share.join(from)), "to": p(&share.join(to)) });

    let res = s
        .post("/api/v1/jobs/copy", Some(&token), body("a.txt", "b.txt"))
        .await;
    assert_eq!(res.status, StatusCode::CONFLICT);
    assert_eq!(res.error_code(), "ALREADY_EXISTS");
    let res = s
        .post("/api/v1/jobs/copy", Some(&token), body("a.txt", "a.txt"))
        .await;
    assert_eq!(res.error_code(), "INVALID_OPERATION");
    let res = s
        .post("/api/v1/jobs/move", Some(&token), body("missing", "x"))
        .await;
    assert_eq!(res.error_code(), "NOT_FOUND");
    let reader = s.token("reader").await;
    let res = s
        .post("/api/v1/jobs/copy", Some(&reader), body("a.txt", "c.txt"))
        .await;
    assert_eq!(res.error_code(), "PERMISSION_DENIED");
    let res = s
        .post("/api/v1/jobs/copy", None, body("a.txt", "c.txt"))
        .await;
    assert_eq!(res.error_code(), "UNAUTHENTICATED");

    // Overwrite replaces the target.
    let res = s
        .post(
            "/api/v1/jobs/copy",
            Some(&token),
            json!({ "from": p(&share.join("a.txt")), "to": p(&share.join("b.txt")), "overwrite": true }),
        )
        .await;
    let done = s.wait_job(res.json()["id"].as_str().unwrap(), &token).await;
    assert_eq!(done["state"], "done");
    assert_eq!(fs::read_to_string(share.join("b.txt")).unwrap(), "a");
    assert!(s.get("/api/info", None).await.json()["features"]
        .as_array()
        .unwrap()
        .contains(&json!("jobs")));
}

#[tokio::test]
async fn jobs_can_be_cancelled() {
    let s = server(true);
    let token = s.token("admin").await;
    let share = s.share();
    // Enough data that the copy is still running when the cancel arrives.
    fs::create_dir(share.join("big")).unwrap();
    for i in 0..400 {
        fs::write(share.join(format!("big/{i}.bin")), vec![7u8; 256 * 1024]).unwrap();
    }
    let res = s
        .post(
            "/api/v1/jobs/copy",
            Some(&token),
            json!({ "from": p(&share.join("big")), "to": p(&share.join("big-copy")) }),
        )
        .await;
    let id = res.json()["id"].as_str().unwrap().to_string();
    let res = s
        .post(
            &format!("/api/v1/jobs/{id}/cancel"),
            Some(&token),
            json!({}),
        )
        .await;
    assert_eq!(res.status, StatusCode::ACCEPTED);
    let job = s.wait_job(&id, &token).await;
    // A very fast disk may finish first; either way nothing partial is left behind.
    assert!(["cancelled", "done"].contains(&job["state"].as_str().unwrap()));
    if job["state"] == "cancelled" {
        assert!(!share.join("big-copy").exists());
    }
    let leftovers = fs::read_dir(&share)
        .unwrap()
        .filter(|e| {
            e.as_ref()
                .unwrap()
                .file_name()
                .to_string_lossy()
                .contains("lunarpart")
        })
        .count();
    assert_eq!(leftovers, 0);

    let res = s
        .post("/api/v1/jobs/nope/cancel", Some(&token), json!({}))
        .await;
    assert_eq!(res.error_code(), "NOT_FOUND");
}

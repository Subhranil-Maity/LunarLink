use crate::core::disks::{disk_for, list_disks, DiskInfo};
use crate::core::entry::{entry_for, Entry};
use crate::core::error::{LunarError, LunarResult};
use crate::core::fs as ops;
use crate::core::list::{list_dir, ListOptions, Listing, SortKey, SortOrder};
use crate::core::path::{Leaf, ResolvedPath, RootKind};
use crate::core::permission::{Permission, Permissions};
use crate::server::api::blocking;
use crate::server::extract::{ApiJson, ApiQuery};
use crate::server::middleware::AuthUser;
use crate::server::state::AppState;
use axum::extract::State;
use axum::http::StatusCode;
use axum::{Extension, Json};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Arc;

use Permission::{Delete, Read, Write};

type AppStateRef = State<Arc<AppState>>;

pub(crate) fn resolve(state: &AppState, raw: &str, leaf: Leaf) -> LunarResult<ResolvedPath> {
    state.roots().resolve(raw, leaf)
}

/// The user's permissions limited by the root's permissions.
pub(crate) fn effective(user: &AuthUser, resolved: &ResolvedPath) -> Permissions {
    user.permissions.intersect(resolved.root.permissions)
}

pub(crate) fn require(
    user: &AuthUser,
    resolved: &ResolvedPath,
    required: &[Permission],
) -> LunarResult<()> {
    effective(user, resolved).require(required)
}

async fn entry(path: PathBuf) -> LunarResult<Entry> {
    blocking(move || Ok(entry_for(&path)?)).await
}

// ---------- roots ----------

#[derive(Serialize)]
pub struct RootInfo {
    name: String,
    path: String,
    kind: RootKind,
    permissions: Permissions,
    disk: Option<DiskInfo>,
}

#[derive(Serialize)]
pub struct RootsResponse {
    roots: Vec<RootInfo>,
}

pub async fn roots(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
) -> LunarResult<Json<RootsResponse>> {
    let (table, disks) = blocking(move || {
        state.refresh_roots();
        Ok((state.roots(), list_disks()))
    })
    .await?;
    let roots = table
        .roots()
        .iter()
        .filter_map(|root| {
            let permissions = user.permissions.intersect(root.permissions);
            permissions.contains(Read).then(|| RootInfo {
                name: root.name.clone(),
                path: root.path.to_string_lossy().into_owned(),
                kind: root.kind,
                permissions,
                disk: disk_for(&root.path, &disks).cloned(),
            })
        })
        .collect();
    Ok(Json(RootsResponse { roots }))
}

// ---------- stat / list ----------

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct StatQuery {
    path: String,
    #[serde(default)]
    follow: bool,
}

pub async fn stat(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiQuery(query): ApiQuery<StatQuery>,
) -> LunarResult<Json<Entry>> {
    let leaf = if query.follow {
        Leaf::Follow
    } else {
        Leaf::NoFollow
    };
    let resolved = resolve(&state, &query.path, leaf)?;
    require(&user, &resolved, &[Read])?;
    Ok(Json(entry(resolved.path).await?))
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ListQuery {
    path: String,
    #[serde(default)]
    offset: usize,
    limit: Option<usize>,
    #[serde(default)]
    sort: SortKey,
    #[serde(default)]
    order: SortOrder,
    dirs_first: Option<bool>,
    show_hidden: Option<bool>,
}

pub async fn list(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiQuery(query): ApiQuery<ListQuery>,
) -> LunarResult<Json<Listing>> {
    let resolved = resolve(&state, &query.path, Leaf::Follow)?;
    require(&user, &resolved, &[Read])?;
    let options = ListOptions {
        offset: query.offset,
        limit: query.limit,
        sort: query.sort,
        order: query.order,
        dirs_first: query.dirs_first.unwrap_or(true),
        show_hidden: query.show_hidden.unwrap_or(true),
    };
    let path = resolved.path;
    Ok(Json(blocking(move || list_dir(&path, &options)).await?))
}

// ---------- create ----------

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateFileBody {
    path: String,
}

pub async fn create_file(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<CreateFileBody>,
) -> LunarResult<(StatusCode, Json<Entry>)> {
    let resolved = resolve(&state, &body.path, Leaf::NoFollow)?;
    require(&user, &resolved, &[Write])?;
    let path = resolved.path;
    let created = blocking(move || {
        ops::create_file(&path)?;
        Ok(entry_for(&path)?)
    })
    .await?;
    Ok((StatusCode::CREATED, Json(created)))
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateDirBody {
    path: String,
    #[serde(default)]
    parents: bool,
}

pub async fn create_dir(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<CreateDirBody>,
) -> LunarResult<(StatusCode, Json<Entry>)> {
    let resolved = resolve(&state, &body.path, Leaf::NoFollow)?;
    require(&user, &resolved, &[Write])?;
    let path = resolved.path;
    let created = blocking(move || {
        ops::create_dir(&path, body.parents)?;
        Ok(entry_for(&path)?)
    })
    .await?;
    Ok((StatusCode::CREATED, Json(created)))
}

// ---------- copy / move ----------

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TransferBody {
    from: String,
    to: String,
    #[serde(default)]
    overwrite: bool,
}

fn resolve_transfer(
    state: &AppState,
    body: &TransferBody,
) -> LunarResult<(ResolvedPath, ResolvedPath)> {
    let from = resolve(state, &body.from, Leaf::NoFollow)?;
    let to = resolve(state, &body.to, Leaf::NoFollow)?;
    if to.is_root() {
        return Err(LunarError::InvalidOperation("A root cannot be replaced."));
    }
    Ok((from, to))
}

pub async fn copy(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<TransferBody>,
) -> LunarResult<(StatusCode, Json<Entry>)> {
    let (from, to) = resolve_transfer(&state, &body)?;
    require(&user, &from, &[Read])?;
    require(&user, &to, &[Write])?;
    let overwrite = body.overwrite;
    let copied = blocking(move || {
        ops::copy(&from.path, &to.path, overwrite)?;
        Ok(entry_for(&to.path)?)
    })
    .await?;
    Ok((StatusCode::CREATED, Json(copied)))
}

pub async fn move_entry(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<TransferBody>,
) -> LunarResult<Json<Entry>> {
    let from = resolve(&state, &body.from, Leaf::NoFollow)?;
    if from.is_root() {
        return Err(LunarError::InvalidOperation("A root cannot be moved."));
    }
    let to = resolve(&state, &body.to, Leaf::NoFollow)?;
    if from.path == to.path {
        require(&user, &from, &[Write])?;
        return Ok(Json(entry(to.path).await?));
    }
    if to.is_root() {
        return Err(LunarError::InvalidOperation("A root cannot be replaced."));
    }
    let is_rename = from.path.parent() == to.path.parent() && Arc::ptr_eq(&from.root, &to.root);
    if is_rename {
        require(&user, &from, &[Write])?;
    } else {
        require(&user, &from, &[Write, Delete])?;
        require(&user, &to, &[Write])?;
    }
    let overwrite = body.overwrite;
    let moved = blocking(move || {
        ops::move_entry(&from.path, &to.path, overwrite)?;
        Ok(entry_for(&to.path)?)
    })
    .await?;
    Ok(Json(moved))
}

// ---------- delete ----------

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DeleteBody {
    path: String,
    #[serde(default)]
    permanent: bool,
    #[serde(default)]
    recursive: bool,
}

pub async fn delete(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<DeleteBody>,
) -> LunarResult<StatusCode> {
    let resolved = resolve(&state, &body.path, Leaf::NoFollow)?;
    if resolved.is_root() {
        return Err(LunarError::InvalidOperation("A root cannot be deleted."));
    }
    require(&user, &resolved, &[Delete])?;
    let path = resolved.path;
    blocking(move || ops::delete(&path, body.permanent, body.recursive)).await?;
    Ok(StatusCode::NO_CONTENT)
}

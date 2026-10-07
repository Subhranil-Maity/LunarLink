use crate::core::error::{LunarError, LunarResult};
use crate::core::fs as ops;
use crate::core::jobs::{Job, JobInfo, JobKind};
use crate::server::api::blocking;
use crate::server::api::fs::{authorize_copy, authorize_move, TransferBody};
use crate::server::extract::ApiJson;
use crate::server::middleware::AuthUser;
use crate::server::state::AppState;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::{Extension, Json};
use serde::Serialize;
use std::sync::Arc;

type AppStateRef = State<Arc<AppState>>;

#[derive(Serialize)]
pub struct JobsResponse {
    jobs: Vec<JobInfo>,
}

pub async fn start_copy(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<TransferBody>,
) -> LunarResult<(StatusCode, Json<JobInfo>)> {
    let (from, to) = authorize_copy(&state, &user, &body)?;
    start(
        &state,
        &user,
        JobKind::Copy,
        from.path,
        to.path,
        body.overwrite,
    )
    .await
}

pub async fn start_move(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    ApiJson(body): ApiJson<TransferBody>,
) -> LunarResult<(StatusCode, Json<JobInfo>)> {
    let (from, to) = authorize_move(&state, &user, &body)?;
    start(
        &state,
        &user,
        JobKind::Move,
        from.path,
        to.path,
        body.overwrite,
    )
    .await
}

/// Checks what can be checked right away (so conflicts are answered synchronously, like the plain
/// endpoints), then runs the job on a blocking thread and returns it.
async fn start(
    state: &AppState,
    user: &AuthUser,
    kind: JobKind,
    from: std::path::PathBuf,
    to: std::path::PathBuf,
    overwrite: bool,
) -> LunarResult<(StatusCode, Json<JobInfo>)> {
    let (check_from, check_to) = (from.clone(), to.clone());
    blocking(move || {
        if kind == JobKind::Copy && check_from == check_to {
            return Err(LunarError::InvalidOperation(
                "The source and destination are the same.",
            ));
        }
        ops::check_transfer_start(&check_from, &check_to, overwrite)
    })
    .await?;
    let job = state
        .jobs
        .insert(Job::new(&user.username, kind, from, to, overwrite))?;
    tracing::info!(job = %job.id, user = %user.username, ?kind, from = %job.from.display(), to = %job.to.display(), "job started");
    let runner = job.clone();
    tokio::task::spawn_blocking(move || runner.run());
    Ok((StatusCode::ACCEPTED, Json(job.info())))
}

pub async fn list(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
) -> Json<JobsResponse> {
    let jobs = state
        .jobs
        .list(&user.username)
        .iter()
        .map(|j| j.info())
        .collect();
    Json(JobsResponse { jobs })
}

pub async fn get(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<String>,
) -> LunarResult<Json<JobInfo>> {
    let job = state
        .jobs
        .get(&id, &user.username)
        .ok_or(LunarError::NotFound)?;
    Ok(Json(job.info()))
}

pub async fn cancel(
    State(state): AppStateRef,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<String>,
) -> LunarResult<(StatusCode, Json<JobInfo>)> {
    let job = state
        .jobs
        .get(&id, &user.username)
        .ok_or(LunarError::NotFound)?;
    job.cancel();
    Ok((StatusCode::ACCEPTED, Json(job.info())))
}

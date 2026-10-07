//! Copies and moves that run in the background, with progress and cancellation.

use crate::core::entry::{entry_for, Entry};
use crate::core::error::{LunarError, LunarResult};
use crate::core::fs::{self as ops, Tracker};
use serde::Serialize;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

/// Finished jobs stay visible this long, so clients can read the result.
const KEEP_FINISHED: Duration = Duration::from_secs(10 * 60);
/// Jobs that haven't finished yet, per user.
pub const MAX_ACTIVE_PER_USER: usize = 8;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum JobKind {
    Copy,
    Move,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum JobState {
    /// Counting what needs to be copied.
    Preparing,
    Running,
    Done,
    Failed,
    Cancelled,
}

impl JobState {
    pub fn is_finished(self) -> bool {
        matches!(self, Self::Done | Self::Failed | Self::Cancelled)
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct JobError {
    pub code: &'static str,
    pub message: String,
}

/// What clients see of a job.
#[derive(Debug, Clone, Serialize)]
pub struct JobInfo {
    pub id: String,
    pub kind: JobKind,
    pub state: JobState,
    pub from: String,
    pub to: String,
    pub bytes_total: u64,
    pub bytes_done: u64,
    pub files_total: u64,
    pub files_done: u64,
    /// Name of the file being copied right now.
    pub current: Option<String>,
    /// The entry at `to`, once done.
    pub entry: Option<Entry>,
    pub error: Option<JobError>,
    pub started_at: u64,
    pub finished_at: Option<u64>,
}

#[derive(Debug, Default)]
struct Status {
    state: Option<JobState>,
    current: Option<String>,
    entry: Option<Entry>,
    error: Option<JobError>,
    finished_at: Option<u64>,
    finished: Option<Instant>,
}

#[derive(Debug)]
pub struct Job {
    pub id: String,
    pub owner: String,
    pub kind: JobKind,
    pub from: PathBuf,
    pub to: PathBuf,
    pub overwrite: bool,
    started_at: u64,
    bytes_total: AtomicU64,
    bytes_done: AtomicU64,
    files_total: AtomicU64,
    files_done: AtomicU64,
    cancel: AtomicBool,
    status: Mutex<Status>,
}

fn now_unix() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

impl Job {
    pub fn new(owner: &str, kind: JobKind, from: PathBuf, to: PathBuf, overwrite: bool) -> Self {
        Self {
            id: format!("{:016x}", rand::random::<u64>()),
            owner: owner.to_string(),
            kind,
            from,
            to,
            overwrite,
            started_at: now_unix(),
            bytes_total: AtomicU64::new(0),
            bytes_done: AtomicU64::new(0),
            files_total: AtomicU64::new(0),
            files_done: AtomicU64::new(0),
            cancel: AtomicBool::new(false),
            status: Mutex::new(Status {
                state: Some(JobState::Preparing),
                ..Default::default()
            }),
        }
    }

    fn status(&self) -> MutexGuard<'_, Status> {
        self.status.lock().unwrap_or_else(|p| p.into_inner())
    }

    pub fn state(&self) -> JobState {
        self.status().state.unwrap_or(JobState::Preparing)
    }

    /// Asks the job to stop. It stops at the next chunk or file and cleans up after itself.
    pub fn cancel(&self) {
        self.cancel.store(true, Ordering::Relaxed);
    }

    fn finished_before(&self, cutoff: Instant) -> bool {
        self.status().finished.is_some_and(|t| t < cutoff)
    }

    pub fn info(&self) -> JobInfo {
        let status = self.status();
        JobInfo {
            id: self.id.clone(),
            kind: self.kind,
            state: status.state.unwrap_or(JobState::Preparing),
            from: self.from.to_string_lossy().into_owned(),
            to: self.to.to_string_lossy().into_owned(),
            bytes_total: self.bytes_total.load(Ordering::Relaxed),
            bytes_done: self.bytes_done.load(Ordering::Relaxed),
            files_total: self.files_total.load(Ordering::Relaxed),
            files_done: self.files_done.load(Ordering::Relaxed),
            current: status.current.clone(),
            entry: status.entry.clone(),
            error: status.error.clone(),
            started_at: self.started_at,
            finished_at: status.finished_at,
        }
    }

    /// Does the work. Blocking; run it on a blocking thread.
    pub fn run(&self) {
        let result = self.work();
        let mut status = self.status();
        status.current = None;
        status.finished = Some(Instant::now());
        status.finished_at = Some(now_unix());
        match result {
            Ok(entry) => {
                status.state = Some(JobState::Done);
                status.entry = Some(entry);
            }
            Err(LunarError::Cancelled) => status.state = Some(JobState::Cancelled),
            Err(e) => {
                tracing::warn!(job = %self.id, error = %e, "job failed");
                status.state = Some(JobState::Failed);
                status.error = Some(JobError {
                    code: e.code(),
                    message: e.message(),
                });
            }
        }
    }

    fn work(&self) -> LunarResult<Entry> {
        let (bytes, files) = ops::measure(&self.from, self)?;
        self.bytes_total.store(bytes, Ordering::Relaxed);
        self.files_total.store(files, Ordering::Relaxed);
        self.status().state = Some(JobState::Running);
        match self.kind {
            JobKind::Copy => ops::copy_tracked(&self.from, &self.to, self.overwrite, self)?,
            JobKind::Move => ops::move_tracked(&self.from, &self.to, self.overwrite, self)?,
        }
        Ok(entry_for(&self.to)?)
    }
}

impl Tracker for Job {
    fn add_bytes(&self, n: u64) {
        self.bytes_done.fetch_add(n, Ordering::Relaxed);
    }

    fn file_started(&self, name: &str) {
        self.status().current = Some(name.to_string());
    }

    fn file_done(&self) {
        self.files_done.fetch_add(1, Ordering::Relaxed);
    }

    fn is_cancelled(&self) -> bool {
        self.cancel.load(Ordering::Relaxed)
    }
}

/// All jobs, by id. Each user only sees their own.
#[derive(Debug, Default)]
pub struct JobRegistry {
    jobs: Mutex<HashMap<String, Arc<Job>>>,
}

impl JobRegistry {
    fn jobs(&self) -> MutexGuard<'_, HashMap<String, Arc<Job>>> {
        self.jobs.lock().unwrap_or_else(|p| p.into_inner())
    }

    /// Registers a new job, unless its owner already has too many running.
    pub fn insert(&self, job: Job) -> LunarResult<Arc<Job>> {
        self.prune(Instant::now());
        let mut jobs = self.jobs();
        let active = jobs
            .values()
            .filter(|j| j.owner == job.owner && !j.state().is_finished())
            .count();
        if active >= MAX_ACTIVE_PER_USER {
            return Err(LunarError::TooManyJobs);
        }
        let job = Arc::new(job);
        jobs.insert(job.id.clone(), job.clone());
        Ok(job)
    }

    /// The job with `id` if it belongs to `owner`.
    pub fn get(&self, id: &str, owner: &str) -> Option<Arc<Job>> {
        self.prune(Instant::now());
        self.jobs().get(id).filter(|j| j.owner == owner).cloned()
    }

    /// `owner`'s jobs, oldest first.
    pub fn list(&self, owner: &str) -> Vec<Arc<Job>> {
        self.prune(Instant::now());
        let mut jobs: Vec<_> = self
            .jobs()
            .values()
            .filter(|j| j.owner == owner)
            .cloned()
            .collect();
        jobs.sort_by_key(|j| j.started_at);
        jobs
    }

    fn prune(&self, now: Instant) {
        if let Some(cutoff) = now.checked_sub(KEEP_FINISHED) {
            self.jobs().retain(|_, j| !j.finished_before(cutoff));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn tmp() -> (tempfile::TempDir, PathBuf) {
        let dir = tempfile::tempdir().unwrap();
        let base = dunce::canonicalize(dir.path()).unwrap();
        (dir, base)
    }

    fn tree(base: &std::path::Path) -> PathBuf {
        let src = base.join("src");
        fs::create_dir_all(src.join("a")).unwrap();
        fs::write(src.join("one.txt"), "12345").unwrap();
        fs::write(src.join("a/two.txt"), "1234567890").unwrap();
        src
    }

    fn leftovers(dir: &std::path::Path) -> Vec<String> {
        fs::read_dir(dir)
            .unwrap()
            .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
            .filter(|n| n.contains("lunarpart"))
            .collect()
    }

    #[test]
    fn copy_job_reports_totals_and_entry() {
        let (_d, base) = tmp();
        let src = tree(&base);
        let job = Job::new("me", JobKind::Copy, src.clone(), base.join("dst"), false);
        job.run();
        let info = job.info();
        assert_eq!(info.state, JobState::Done);
        assert_eq!((info.bytes_total, info.bytes_done), (15, 15));
        assert_eq!((info.files_total, info.files_done), (2, 2));
        assert_eq!(info.entry.unwrap().name, "dst");
        assert!(info.current.is_none());
        assert!(info.finished_at.is_some());
        assert_eq!(
            fs::read_to_string(base.join("dst/a/two.txt")).unwrap(),
            "1234567890"
        );
        assert!(src.exists());
        assert!(leftovers(&base).is_empty());
    }

    #[test]
    fn move_job_renames() {
        let (_d, base) = tmp();
        let src = tree(&base);
        let job = Job::new("me", JobKind::Move, src.clone(), base.join("moved"), false);
        job.run();
        let info = job.info();
        assert_eq!(info.state, JobState::Done);
        assert_eq!(info.bytes_done, 15);
        assert_eq!(info.files_done, 2);
        assert!(!src.exists());
        assert!(base.join("moved/one.txt").exists());
        assert!(leftovers(&base).is_empty());
    }

    #[test]
    fn cancelled_copy_keeps_the_old_target_and_leaves_nothing_behind() {
        let (_d, base) = tmp();
        let src = tree(&base);
        let dst = base.join("dst");
        fs::create_dir(&dst).unwrap();
        fs::write(dst.join("old.txt"), "old").unwrap();
        let job = Job::new("me", JobKind::Copy, src, dst.clone(), true);
        job.cancel();
        job.run();
        let info = job.info();
        assert_eq!(info.state, JobState::Cancelled);
        assert!(info.error.is_none());
        assert!(dst.join("old.txt").exists());
        assert!(leftovers(&base).is_empty());
    }

    /// Cancels once the first file has been started.
    struct CancelAfterFirstFile(AtomicBool);

    impl Tracker for CancelAfterFirstFile {
        fn file_started(&self, _: &str) {
            self.0.store(true, Ordering::Relaxed);
        }
        fn is_cancelled(&self) -> bool {
            self.0.load(Ordering::Relaxed)
        }
    }

    #[test]
    fn cancelling_mid_copy_removes_the_partial_copy() {
        let (_d, base) = tmp();
        let src = tree(&base);
        let result = ops::copy_tracked(
            &src,
            &base.join("dst"),
            false,
            &CancelAfterFirstFile(AtomicBool::new(false)),
        );
        assert!(matches!(result, Err(LunarError::Cancelled)));
        assert!(!base.join("dst").exists());
        assert!(leftovers(&base).is_empty());
    }

    #[test]
    fn big_files_are_copied_in_chunks() {
        let (_d, base) = tmp();
        let big = base.join("big.bin");
        let data: Vec<u8> = (0..(ops::CHUNKED_COPY_MIN + 3))
            .map(|i| (i % 251) as u8)
            .collect();
        fs::write(&big, &data).unwrap();
        let job = Job::new("me", JobKind::Copy, big, base.join("copy.bin"), false);
        job.run();
        assert_eq!(job.info().bytes_done, data.len() as u64);
        assert_eq!(fs::read(base.join("copy.bin")).unwrap(), data);
    }

    #[test]
    fn failures_are_reported_with_public_codes() {
        let (_d, base) = tmp();
        let job = Job::new(
            "me",
            JobKind::Copy,
            base.join("missing"),
            base.join("x"),
            false,
        );
        job.run();
        let info = job.info();
        assert_eq!(info.state, JobState::Failed);
        assert_eq!(info.error.unwrap().code, "NOT_FOUND");
    }

    #[test]
    fn registry_scopes_jobs_to_their_owner_and_limits_active_ones() {
        let registry = JobRegistry::default();
        let make = |owner: &str| Job::new(owner, JobKind::Copy, "/a".into(), "/b".into(), false);
        let mine = registry.insert(make("me")).unwrap();
        assert!(registry.get(&mine.id, "me").is_some());
        assert!(registry.get(&mine.id, "you").is_none());
        assert_eq!(registry.list("you").len(), 0);

        for _ in 1..MAX_ACTIVE_PER_USER {
            registry.insert(make("me")).unwrap();
        }
        assert!(matches!(
            registry.insert(make("me")),
            Err(LunarError::TooManyJobs)
        ));
        assert!(registry.insert(make("you")).is_ok());

        // Finished jobs don't count, and disappear after a while.
        mine.run();
        assert!(registry.insert(make("me")).is_ok());
        registry.prune(Instant::now() + KEEP_FINISHED + Duration::from_secs(1));
        assert!(registry.get(&mine.id, "me").is_none());
        assert_eq!(registry.list("me").len(), MAX_ACTIVE_PER_USER);
    }
}

//! Per-username login rate limiting.
//!
//! Limiting by username (not IP) keeps working behind a reverse proxy, where every request
//! comes from the proxy's address. Only usernames that exist in the config are tracked, so
//! the map can't be grown by arbitrary input.

use std::collections::HashMap;
use std::sync::Mutex;
use std::time::{Duration, Instant};

const FREE_ATTEMPTS: u32 = 5;
const BASE_LOCK: Duration = Duration::from_secs(1);
const MAX_LOCK: Duration = Duration::from_secs(300);
const FORGET_AFTER: Duration = Duration::from_secs(15 * 60);

#[derive(Debug, Default)]
pub struct LoginGuard {
    entries: Mutex<HashMap<String, Attempts>>,
}

#[derive(Debug)]
struct Attempts {
    failures: u32,
    last_failure: Instant,
    locked_until: Option<Instant>,
}

impl LoginGuard {
    /// `Err(wait)` while the username is locked.
    pub fn check(&self, username: &str) -> Result<(), Duration> {
        self.check_at(username, Instant::now())
    }

    pub fn record_failure(&self, username: &str) {
        self.record_failure_at(username, Instant::now())
    }

    pub fn record_success(&self, username: &str) {
        self.lock().remove(username);
    }

    pub fn check_at(&self, username: &str, now: Instant) -> Result<(), Duration> {
        match self.lock().get(username).and_then(|a| a.locked_until) {
            Some(until) if until > now => Err(until - now),
            _ => Ok(()),
        }
    }

    pub fn record_failure_at(&self, username: &str, now: Instant) {
        let mut entries = self.lock();
        let attempts = entries.entry(username.to_string()).or_insert(Attempts {
            failures: 0,
            last_failure: now,
            locked_until: None,
        });
        if now.duration_since(attempts.last_failure) > FORGET_AFTER {
            attempts.failures = 0;
        }
        attempts.failures += 1;
        attempts.last_failure = now;
        if attempts.failures >= FREE_ATTEMPTS {
            let exponent = (attempts.failures - FREE_ATTEMPTS).min(16);
            let lock = BASE_LOCK.saturating_mul(1 << exponent).min(MAX_LOCK);
            attempts.locked_until = Some(now + lock);
        }
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<String, Attempts>> {
        self.entries
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn locks_after_free_attempts_with_backoff() {
        let guard = LoginGuard::default();
        let t0 = Instant::now();
        for _ in 0..FREE_ATTEMPTS - 1 {
            guard.record_failure_at("a", t0);
            assert!(guard.check_at("a", t0).is_ok());
        }
        guard.record_failure_at("a", t0);
        assert_eq!(guard.check_at("a", t0), Err(Duration::from_secs(1)));
        assert!(guard.check_at("a", t0 + Duration::from_secs(1)).is_ok());

        let t1 = t0 + Duration::from_secs(2);
        guard.record_failure_at("a", t1);
        assert_eq!(guard.check_at("a", t1), Err(Duration::from_secs(2)));

        // Other users are unaffected.
        assert!(guard.check_at("b", t1).is_ok());
    }

    #[test]
    fn lock_is_capped() {
        let guard = LoginGuard::default();
        let t0 = Instant::now();
        for _ in 0..40 {
            guard.record_failure_at("a", t0);
        }
        assert_eq!(guard.check_at("a", t0), Err(MAX_LOCK));
    }

    #[test]
    fn success_and_time_reset() {
        let guard = LoginGuard::default();
        let t0 = Instant::now();
        for _ in 0..FREE_ATTEMPTS {
            guard.record_failure_at("a", t0);
        }
        guard.record_success("a");
        assert!(guard.check_at("a", t0).is_ok());

        for _ in 0..FREE_ATTEMPTS - 1 {
            guard.record_failure_at("a", t0);
        }
        let later = t0 + FORGET_AFTER + Duration::from_secs(1);
        guard.record_failure_at("a", later);
        assert!(guard.check_at("a", later).is_ok());
    }
}

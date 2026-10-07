use crate::auth::login_guard::LoginGuard;
use crate::config::LunarConfig;
use crate::core::disks::list_disks;
use crate::core::path::RootTable;
use crate::core::thumbs::Thumbnailer;
use std::path::PathBuf;
use std::sync::{Arc, RwLock};
use tokio::sync::Semaphore;

/// How many previews are made at the same time, so a fast scroll through a photo folder
/// doesn't take every request slot or CPU core.
const THUMBNAIL_WORKERS: usize = 4;

pub struct AppState {
    pub config: LunarConfig,
    pub login_guard: LoginGuard,
    roots: RwLock<Arc<RootTable>>,
    /// `None` when thumbnails are turned off in the config.
    pub thumbnails: Option<Arc<Thumbnailer>>,
    pub thumbnail_permits: Semaphore,
}

impl AppState {
    /// Builds the root table from the config (touches the filesystem).
    pub fn new(config: LunarConfig) -> Arc<Self> {
        let roots = build_roots(&config);
        Self::with_roots(config, roots)
    }

    pub fn with_roots(config: LunarConfig, roots: RootTable) -> Arc<Self> {
        let thumbnails = config.thumbnails.enabled.then(|| {
            let cache_dir = match &config.thumbnails.cache_dir {
                Some(dir) => Some(PathBuf::from(dir)),
                None => Thumbnailer::default_cache_dir(),
            };
            let max_bytes = config.thumbnails.max_source_mb.saturating_mul(1024 * 1024);
            Arc::new(Thumbnailer::new(cache_dir, max_bytes))
        });
        Arc::new(Self {
            config,
            login_guard: LoginGuard::default(),
            roots: RwLock::new(Arc::new(roots)),
            thumbnails,
            thumbnail_permits: Semaphore::new(THUMBNAIL_WORKERS),
        })
    }

    pub fn roots(&self) -> Arc<RootTable> {
        self.roots
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .clone()
    }

    /// Re-reads the disk list so newly attached drives show up. Only needed when drives are
    /// exposed; mount roots don't change while the server runs.
    pub fn refresh_roots(&self) {
        if !self.config.roots.expose_drives {
            return;
        }
        let roots = Arc::new(build_roots(&self.config));
        *self
            .roots
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner()) = roots;
    }
}

fn build_roots(config: &LunarConfig) -> RootTable {
    let disks = if config.roots.expose_drives {
        list_disks()
    } else {
        vec![]
    };
    RootTable::from_config(&config.roots, &disks)
}

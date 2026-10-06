use crate::auth::login_guard::LoginGuard;
use crate::config::LunarConfig;
use crate::core::disks::list_disks;
use crate::core::path::RootTable;
use std::sync::{Arc, RwLock};

pub struct AppState {
    pub config: LunarConfig,
    pub login_guard: LoginGuard,
    roots: RwLock<Arc<RootTable>>,
}

impl AppState {
    /// Builds the root table from the config (touches the filesystem).
    pub fn new(config: LunarConfig) -> Arc<Self> {
        let roots = build_roots(&config);
        Self::with_roots(config, roots)
    }

    pub fn with_roots(config: LunarConfig, roots: RootTable) -> Arc<Self> {
        Arc::new(Self {
            config,
            login_guard: LoginGuard::default(),
            roots: RwLock::new(Arc::new(roots)),
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

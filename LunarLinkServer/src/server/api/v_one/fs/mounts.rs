use crate::config::LunarConfig;
use crate::core::fs::get_metadata;
use crate::core::lunar_error::LunarResult;
use crate::core::types::drive::get_drives;
use crate::core::types::{LunarMetadata, MountPoints};
use crate::server::utils::expand_env_vars;
use axum::{Extension, Json};
use std::path::PathBuf;

pub async fn get_mounts(
    Extension(config): Extension<LunarConfig>,
) -> LunarResult<Json<MountPoints>> {
    let drives = get_drives();
    let mut mounts: Vec<LunarMetadata> = vec![];
    for d in config.mounts.custom_mounts {
        let path = &PathBuf::from(expand_env_vars(d.path.as_str()));
        if !path.exists() {
            continue;
        }
        mounts.push(get_metadata(path).await?)
    }
    let mounts_point = MountPoints { drives, mounts };

    Ok(Json(mounts_point))
}

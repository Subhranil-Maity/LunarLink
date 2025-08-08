use std::path::{Path, PathBuf};
use axum::{Extension, Json};
use crate::config::LunarConfig;
use crate::core::fs::get_metadata;
use crate::core::lunar_error::LunarResult;
use crate::core::types::drive::{get_drives, Drive};
use crate::core::types::{EntryType, LunarMetadata, MountPoints};

pub async fn get_mounts(
    Extension(config): Extension<LunarConfig>,
) -> LunarResult<Json<MountPoints>> {
    let drives = get_drives();
    let mut mounts: Vec<LunarMetadata> = vec![];
    for d in config.mounts.custom_mounts{
        let path = &PathBuf::from(&d.path);
        mounts.push(
            get_metadata(path).await?
        )
    }
    let mounts_point = MountPoints {
        drives,
        mounts,
    };


    Ok(Json(mounts_point))
}
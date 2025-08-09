use crate::core::lunar_error::{LunarError, LunarResult};
use serde::Deserialize;

#[derive(Deserialize)]
pub struct QueryParams {
    pub path: Option<String>,
    pub new_path: Option<String>,
    pub should_override: Option<bool>,
    pub permanent: Option<bool>,
}

impl QueryParams {
    pub fn get_path(&self) -> LunarResult<String> {
        return match &self.path {
            None => {
                return Err(LunarError::PathNotProvided);
            }
            Some(path) => Ok(path.clone()),
        };
    }
    pub fn get_new_path(&self) -> LunarResult<String> {
        return match &self.new_path {
            None => {
                return Err(LunarError::NewPathNotProvided);
            }
            Some(path) => Ok(path.clone()),
        };
    }
    pub fn should_override(&self) -> bool {
        return match &self.should_override {
            None => false,
            Some(override_value) => *override_value,
        };
    }
    pub fn is_permanent(&self) -> bool {
        return match &self.permanent {
            None => false,
            Some(permanent_value) => *permanent_value,
        };
    }
}

//! `Json` and `Query` wrappers whose rejections use the API's JSON error format.

use crate::core::error::LunarError;
use axum::extract::{FromRequest, FromRequestParts};

#[derive(FromRequest)]
#[from_request(via(axum::Json), rejection(LunarError))]
pub struct ApiJson<T>(pub T);

#[derive(FromRequestParts)]
#[from_request(via(axum::extract::Query), rejection(LunarError))]
pub struct ApiQuery<T>(pub T);

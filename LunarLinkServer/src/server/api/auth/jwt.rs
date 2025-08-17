use crate::config::UserPermission;
use crate::core::lunar_error::{LunarError, LunarResult};
use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, TokenData, Validation};
use serde::{Deserialize, Serialize};
#[derive(Serialize, Deserialize)]
pub struct Claims {
    pub exp: usize,
    pub iat: usize,
    pub jwt_payload: JwtPayload,
}
#[derive(Serialize, Deserialize)]
pub struct JwtPayload {
    pub user_name: String,
    pub permissions: Vec<UserPermission>,
}

pub fn encode_jwt(jwt_payload: JwtPayload, secret: &[u8]) -> LunarResult<String> {
    let now = Utc::now();
    let expire = Duration::hours(24);

    let claim = Claims {
        iat: now.timestamp() as usize,
        exp: (now + expire).timestamp() as usize,
        jwt_payload,
    };

    encode(
        &Header::default(),
        &claim,
        &EncodingKey::from_secret(secret),
    )
    .map_err(|e| LunarError::KnownUnknownError(format!("{:?}", e)))
}

pub fn decode_jwt(jwt: String, secret: &[u8]) -> LunarResult<TokenData<Claims>> {
    let res: LunarResult<TokenData<Claims>> = decode(
        &jwt,
        &DecodingKey::from_secret(secret),
        &Validation::default(),
    )
    // .map_err(|e| LunarError::KnownUnknownError(format!("{:?}", e)));
    .map_err(|e| LunarError::JwtError(format!("{:?}", e)));
    return res;
}

use crate::core::error::{LunarError, LunarResult};
use chrono::Utc;
use jsonwebtoken::errors::ErrorKind;
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

/// Tokens only identify the user. Permissions are looked up from the config on each request.
#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,
    pub iat: u64,
    pub exp: u64,
}

/// Returns the token and its expiry (Unix seconds).
pub fn issue(username: &str, ttl_minutes: u32, secret: &[u8]) -> LunarResult<(String, u64)> {
    let now = Utc::now().timestamp().max(0) as u64;
    let claims = Claims {
        sub: username.to_string(),
        iat: now,
        exp: now + u64::from(ttl_minutes) * 60,
    };
    Ok((encode_claims(&claims, secret)?, claims.exp))
}

pub fn encode_claims(claims: &Claims, secret: &[u8]) -> LunarResult<String> {
    encode(
        &Header::new(Algorithm::HS256),
        claims,
        &EncodingKey::from_secret(secret),
    )
    .map_err(|e| LunarError::Internal(e.to_string()))
}

pub fn verify(token: &str, secret: &[u8]) -> LunarResult<Claims> {
    let mut validation = Validation::new(Algorithm::HS256);
    validation.leeway = 0;
    validation.set_required_spec_claims(&["exp", "sub"]);
    decode::<Claims>(token, &DecodingKey::from_secret(secret), &validation)
        .map(|data| data.claims)
        .map_err(|e| match e.kind() {
            ErrorKind::ExpiredSignature => LunarError::TokenExpired,
            _ => LunarError::InvalidToken,
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    const SECRET: &[u8] = b"test-secret-test-secret-test-secret";

    #[test]
    fn round_trip() {
        let (token, exp) = issue("alice", 5, SECRET).unwrap();
        let claims = verify(&token, SECRET).unwrap();
        assert_eq!(claims.sub, "alice");
        assert_eq!(claims.exp, exp);
        assert_eq!(claims.exp - claims.iat, 300);
    }

    #[test]
    fn rejects_wrong_secret_and_garbage() {
        let (token, _) = issue("alice", 5, SECRET).unwrap();
        assert!(matches!(
            verify(&token, b"another-secret-another-secret-xx"),
            Err(LunarError::InvalidToken)
        ));
        assert!(matches!(
            verify("not.a.token", SECRET),
            Err(LunarError::InvalidToken)
        ));
    }

    #[test]
    fn rejects_expired() {
        let now = Utc::now().timestamp() as u64;
        let claims = Claims {
            sub: "alice".into(),
            iat: now - 100,
            exp: now - 10,
        };
        let token = encode_claims(&claims, SECRET).unwrap();
        assert!(matches!(
            verify(&token, SECRET),
            Err(LunarError::TokenExpired)
        ));
    }
}

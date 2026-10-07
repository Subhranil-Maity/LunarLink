//! Announces the server on the local network (mDNS / DNS-SD), so apps can find it without typing an
//! address. Service type `_lunarlink._tcp`; TXT keys `api`, `auth` (`1`/`0`), `os` and `ver`.
//! Announced over IPv4 (see [`Advertisement::start`]).

use crate::server::api::info::{API_VERSION, SERVER_VERSION};
use mdns_sd::{IfKind, ServiceDaemon, ServiceInfo};
use std::net::IpAddr;
use std::time::Duration;

pub const SERVICE_TYPE: &str = "_lunarlink._tcp.local.";

/// DNS-SD instance names are limited to 63 bytes.
const MAX_INSTANCE_NAME: usize = 63;

pub struct Advertisement {
    daemon: ServiceDaemon,
    fullname: String,
}

impl Advertisement {
    /// Starts announcing. `bind_host` is the configured listen address: a wildcard announces every
    /// LAN address, a specific address announces only that one, and loopback isn't announced.
    pub fn start(
        name: &str,
        bind_host: &str,
        port: u16,
        auth_required: bool,
    ) -> anyhow::Result<Option<Self>> {
        let bind: Option<IpAddr> = bind_host.parse().ok();
        if bind_host == "localhost" || bind.is_some_and(|ip| ip.is_loopback()) {
            tracing::info!(
                "not announcing on the local network: the server only listens on loopback"
            );
            return Ok(None);
        }
        let wildcard = bind.is_some_and(|ip| ip.is_unspecified());

        let daemon = ServiceDaemon::new()?;
        // Answer over IPv4 only, unless the server listens on a specific IPv6 address. mDNS answers
        // carry only the addresses of the family the question came in on, and Android often asks over
        // IPv6, so it would never learn the IPv4 address: the phone then shows a long (and often
        // rotating) IPv6 address and doesn't recognise a server saved by its IPv4 address.
        if !bind.is_some_and(|ip| ip.is_ipv6() && !ip.is_unspecified()) {
            daemon.disable_interface(IfKind::IPv6)?;
        }
        let api = API_VERSION.to_string();
        let properties = [
            ("api", api.as_str()),
            ("auth", if auth_required { "1" } else { "0" }),
            ("os", std::env::consts::OS),
            ("ver", SERVER_VERSION),
        ];
        let instance = instance_name(name);
        let host = format!("{}.local.", host_label(name));
        let info = match bind {
            Some(ip) if !wildcard => {
                ServiceInfo::new(SERVICE_TYPE, &instance, &host, ip, port, &properties[..])?
            }
            _ => ServiceInfo::new(SERVICE_TYPE, &instance, &host, (), port, &properties[..])?
                .enable_addr_auto(),
        };
        let fullname = info.get_fullname().to_string();
        daemon.register(info)?;
        tracing::info!(name = %instance, "announcing on the local network");
        Ok(Some(Self { daemon, fullname }))
    }

    /// Withdraws the announcement, so apps stop listing the server right away.
    pub fn stop(self) {
        if let Ok(done) = self.daemon.unregister(&self.fullname) {
            let _ = done.recv_timeout(Duration::from_secs(1));
        }
        let _ = self.daemon.shutdown();
    }
}

/// The name apps show, cut to the DNS-SD limit on a character boundary.
fn instance_name(name: &str) -> String {
    let mut end = name.len().min(MAX_INSTANCE_NAME);
    while !name.is_char_boundary(end) {
        end -= 1;
    }
    let trimmed = name[..end].trim();
    if trimmed.is_empty() {
        "LunarLink".into()
    } else {
        trimmed.to_string()
    }
}

/// A valid host label (letters, digits, hyphens) derived from the name.
fn host_label(name: &str) -> String {
    let label: String = name
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() {
                c.to_ascii_lowercase()
            } else {
                '-'
            }
        })
        .collect::<String>()
        .split('-')
        .filter(|part| !part.is_empty())
        .collect::<Vec<_>>()
        .join("-");
    let label: String = label.chars().take(MAX_INSTANCE_NAME).collect();
    if label.is_empty() {
        "lunarlink".into()
    } else {
        format!("{label}-lunarlink")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn names_are_made_valid() {
        assert_eq!(instance_name("My Desktop"), "My Desktop");
        assert_eq!(instance_name("   "), "LunarLink");
        let long = "é".repeat(40); // 80 bytes
        let cut = instance_name(&long);
        assert!(cut.len() <= MAX_INSTANCE_NAME);
        assert!(cut.chars().all(|c| c == 'é'));

        assert_eq!(host_label("My Desktop!"), "my-desktop-lunarlink");
        assert_eq!(host_label("ñ"), "lunarlink");
    }

    #[test]
    fn loopback_is_not_announced() {
        assert!(Advertisement::start("x", "127.0.0.1", 1, true)
            .unwrap()
            .is_none());
        assert!(Advertisement::start("x", "::1", 1, true).unwrap().is_none());
        assert!(Advertisement::start("x", "localhost", 1, true)
            .unwrap()
            .is_none());
    }
}

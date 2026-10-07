# TODO

## Give each device a stable identity

Devices are currently identified by their network address (IP and port), not by the device itself.

- The app stores a saved server by its base URL (`http://<ip>:<port>`) and finds duplicates by
  address and username.
- The "On this network" list hides servers that are already saved by comparing addresses.
- Downloads remember which server they belong to through the saved entry, which is keyed by address.

So when a server's IP changes (DHCP, another Wi-Fi network, IPv4 vs IPv6), the app sees a different
server: the saved entry stops working, the same machine appears again under "On this network", and
it can be saved twice. Two servers behind the same address, one after the other, would be mixed up.

Plan:

- Server: on first start, generate a random device id (e.g. a UUID) and keep it next to the
  config so it survives restarts. Expose it in `GET /api/info` (`device_id`) and in the mDNS TXT
  record (`id=`).
- App: save the device id with each server. Match saved servers, nearby servers and duplicates by
  id instead of address. When a saved server is found under a new address, update its address.
- Later: give each app install an id too, so the server can tell clients apart (per-device sessions,
  revoking a lost phone) instead of only seeing an IP.

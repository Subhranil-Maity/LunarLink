# LunarLink

LunarLink is a self-hosted remote file management project with two parts:

- **LunarLinkServer**: a Rust server that exposes authenticated filesystem APIs.
- **LunarExplorer**: an Android app (Kotlin + Jetpack Compose) that connects to the server and provides a file explorer interface.

## What It Does

LunarLink lets you run a server on your machine and browse/manage its files from an Android device.

Core capabilities include:

- User login with JWT-based authentication
- Mount and drive discovery
- Folder browsing and metadata lookup
- File and folder operations (create, rename, move, copy, delete)
- File reading and chunked downloads
- Download tracking in the Android app

## Repository Structure

- `LunarLinkServer/` - Rust backend service and configuration
- `LunarExplorer/` - Android client application
- `LICENSE` - project license

## Quick Start

### 1. Run the server

```bash
cd LunarLinkServer
cargo run --bin lunar_link
```

On first run, the server creates `config.toml` if it is missing.

### 2. Configure server settings

Edit `LunarLinkServer/config.toml` to set:

- Host and port
- Users and passwords
- Mount points

### 3. Run the Android app

```bash
cd LunarExplorer
./gradlew assembleDebug
```

Or open `LunarExplorer` in Android Studio and run the app on a device/emulator.

## API Overview

Main server routes:

- `POST /api/auth/login`
- `GET /api/get_info`
- `GET /api/v1/fs/mounts`
- `GET /api/v1/fs/metadata`
- `GET /api/v1/fs/get_folder_content`
- `GET /api/v1/fs/read_full_file`
- `GET /api/v1/fs/download_file`
- `PATCH /api/v1/fs/rename`
- `PATCH /api/v1/fs/move`
- `POST /api/v1/fs/copy`
- `DELETE /api/v1/fs/delete`
- `POST /api/v1/fs/touch`
- `POST /api/v1/fs/mkdir`

## Technology Stack

- **Server**: Rust, Tokio, Axum, Serde, JSON Web Tokens
- **Android App**: Kotlin, Jetpack Compose, Ktor Client, Koin, DataStore

## License

This project is licensed under the terms in `LICENSE`.

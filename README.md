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
- Big copies and moves run in the background with live progress and Cancel
- Search by name in a folder and all its subfolders
- Image previews (thumbnails) in a list or grid view
- Resumable downloads with progress notifications in the Android app
- Servers on the same network show up in the app automatically (mDNS)

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

- Host and port, and whether the server announces itself on the local network
- Users and passwords
- Mount points
- Thumbnail cache

### 3. Run the Android app

```bash
cd LunarExplorer
./gradlew assembleDebug
```

Or open `LunarExplorer` in Android Studio and run the app on a device/emulator.

## API Overview

Main server routes (full reference: [`LunarLinkServer/API.md`](LunarLinkServer/API.md)):

- `GET /api/info`, `POST /api/auth/login`, `GET /api/auth/me`
- `GET /api/v1/fs/roots`, `stat`, `list`, `search`, `download`, `thumbnail`
- `POST /api/v1/fs/file`, `dir`, `copy`, `move`, `delete`
- `POST /api/v1/jobs/copy`, `POST /api/v1/jobs/move`, `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel`

## Technology Stack

- **Server**: Rust, Tokio, Axum, Serde, JSON Web Tokens
- **Android App**: Kotlin, Jetpack Compose, Ktor Client, Koin, DataStore

## License

This project is licensed under the terms in `LICENSE`.

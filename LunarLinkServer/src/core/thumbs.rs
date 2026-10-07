//! Small previews of images, cached on disk.

use crate::core::error::{LunarError, LunarResult};
use image::{DynamicImage, ImageDecoder, ImageFormat, ImageReader};
use std::collections::hash_map::DefaultHasher;
use std::fs;
use std::hash::{Hash, Hasher};
use std::io::Cursor;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

pub const MIN_SIZE: u32 = 64;
pub const MAX_SIZE: u32 = 512;
pub const DEFAULT_SIZE: u32 = 256;

/// Cached previews older than this are removed at startup.
const CACHE_MAX_AGE: Duration = Duration::from_secs(30 * 24 * 60 * 60);
const JPEG_QUALITY: u8 = 80;

#[derive(Debug, Clone)]
pub struct Thumbnail {
    pub bytes: Vec<u8>,
    pub content_type: &'static str,
}

#[derive(Debug)]
pub struct Thumbnailer {
    /// `None` when there is no usable cache folder; previews are then made on every request.
    cache_dir: Option<PathBuf>,
    max_source_bytes: u64,
}

impl Thumbnailer {
    pub fn new(cache_dir: Option<PathBuf>, max_source_bytes: u64) -> Self {
        let cache_dir = cache_dir.and_then(|dir| match fs::create_dir_all(&dir) {
            Ok(()) => Some(dir),
            Err(e) => {
                tracing::warn!(error = %e, dir = %dir.display(), "can't create the thumbnail cache, previews won't be cached");
                None
            }
        });
        Self {
            cache_dir,
            max_source_bytes,
        }
    }

    /// The default cache folder, e.g. `~/.cache/lunarlink/thumbs` on Linux.
    pub fn default_cache_dir() -> Option<PathBuf> {
        dirs::cache_dir().map(|d| d.join("lunarlink").join("thumbs"))
    }

    pub fn cache_dir(&self) -> Option<&Path> {
        self.cache_dir.as_deref()
    }

    /// A preview of the image at `source` that fits in `size`×`size` pixels. JPEG, or PNG when the image
    /// has transparency. Results are cached by path, modification time, file size and `size`.
    pub fn thumbnail(&self, source: &Path, size: u32) -> LunarResult<Thumbnail> {
        let size = size.clamp(MIN_SIZE, MAX_SIZE);
        let meta = fs::metadata(source)?;
        if meta.is_dir() {
            return Err(LunarError::IsADirectory);
        }
        let format = ImageFormat::from_path(source)
            .ok()
            .filter(|f| f.reading_enabled())
            .ok_or_else(|| LunarError::ThumbnailUnavailable("not a supported image".into()))?;
        if meta.len() > self.max_source_bytes {
            return Err(LunarError::ThumbnailUnavailable("image is too big".into()));
        }

        let key = cache_key(source, &meta, size);
        if let Some(cached) = self.read_cached(&key) {
            return Ok(cached);
        }
        let thumb = render(source, format, size)?;
        self.write_cached(&key, &thumb);
        Ok(thumb)
    }

    fn cached_path(&self, key: &str, content_type: &str) -> Option<PathBuf> {
        let ext = if content_type == "image/png" {
            "png"
        } else {
            "jpg"
        };
        self.cache_dir
            .as_ref()
            .map(|d| d.join(format!("{key}.{ext}")))
    }

    fn read_cached(&self, key: &str) -> Option<Thumbnail> {
        ["image/jpeg", "image/png"]
            .into_iter()
            .find_map(|content_type| {
                let bytes = fs::read(self.cached_path(key, content_type)?).ok()?;
                Some(Thumbnail {
                    bytes,
                    content_type,
                })
            })
    }

    fn write_cached(&self, key: &str, thumb: &Thumbnail) {
        let Some(path) = self.cached_path(key, thumb.content_type) else {
            return;
        };
        // Write to a temporary name first so a reader never sees a half-written file.
        let tmp = path.with_extension(format!("tmp{}", rand::random::<u32>()));
        let result = fs::write(&tmp, &thumb.bytes).and_then(|()| fs::rename(&tmp, &path));
        if let Err(e) = result {
            let _ = fs::remove_file(&tmp);
            tracing::debug!(error = %e, "could not cache thumbnail");
        }
    }

    /// Removes cached previews that haven't been written for a month.
    pub fn prune_cache(&self) {
        let Some(dir) = &self.cache_dir else {
            return;
        };
        let Ok(entries) = fs::read_dir(dir) else {
            return;
        };
        let cutoff = SystemTime::now() - CACHE_MAX_AGE;
        let mut removed = 0;
        for entry in entries.flatten() {
            let old = entry
                .metadata()
                .and_then(|m| m.modified())
                .is_ok_and(|t| t < cutoff);
            if old && fs::remove_file(entry.path()).is_ok() {
                removed += 1;
            }
        }
        if removed > 0 {
            tracing::info!(removed, "pruned old thumbnails");
        }
    }
}

fn cache_key(source: &Path, meta: &fs::Metadata, size: u32) -> String {
    let mut hasher = DefaultHasher::new();
    source.hash(&mut hasher);
    meta.len().hash(&mut hasher);
    meta.modified()
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_nanos())
        .hash(&mut hasher);
    size.hash(&mut hasher);
    format!("{:016x}", hasher.finish())
}

fn unavailable(e: impl std::fmt::Display) -> LunarError {
    LunarError::ThumbnailUnavailable(e.to_string())
}

fn render(source: &Path, format: ImageFormat, size: u32) -> LunarResult<Thumbnail> {
    let mut reader = ImageReader::open(source)?;
    reader.set_format(format);
    let mut decoder = reader.into_decoder().map_err(unavailable)?;
    let orientation = decoder.orientation().map_err(unavailable)?;
    let mut image = DynamicImage::from_decoder(decoder).map_err(unavailable)?;
    image.apply_orientation(orientation);

    // Shrink to fit, but never enlarge an image that is already small enough.
    let small = if image.width() <= size && image.height() <= size {
        image
    } else {
        image.thumbnail(size, size)
    };
    let mut bytes = Vec::new();
    let content_type = if small.color().has_alpha() {
        small
            .write_to(&mut Cursor::new(&mut bytes), ImageFormat::Png)
            .map_err(unavailable)?;
        "image/png"
    } else {
        let rgb = small.into_rgb8();
        let encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut bytes, JPEG_QUALITY);
        rgb.write_with_encoder(encoder).map_err(unavailable)?;
        "image/jpeg"
    };
    Ok(Thumbnail {
        bytes,
        content_type,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use image::{GenericImageView, ImageBuffer, Rgb, Rgba};

    fn setup() -> (tempfile::TempDir, PathBuf, Thumbnailer) {
        let dir = tempfile::tempdir().unwrap();
        let base = dunce::canonicalize(dir.path()).unwrap();
        let thumbs = Thumbnailer::new(Some(base.join("cache")), 10 * 1024 * 1024);
        (dir, base, thumbs)
    }

    fn write_png(path: &Path, w: u32, h: u32) {
        ImageBuffer::from_fn(w, h, |x, _| Rgb([(x % 256) as u8, 10, 200]))
            .save(path)
            .unwrap();
    }

    fn decode(t: &Thumbnail) -> DynamicImage {
        image::load_from_memory(&t.bytes).unwrap()
    }

    fn cached_files(thumbs: &Thumbnailer) -> usize {
        fs::read_dir(thumbs.cache_dir().unwrap()).unwrap().count()
    }

    #[test]
    fn fits_the_requested_size_and_keeps_aspect_ratio() {
        let (_d, base, thumbs) = setup();
        let photo = base.join("wide.png");
        write_png(&photo, 800, 400);
        let t = thumbs.thumbnail(&photo, 200).unwrap();
        assert_eq!(t.content_type, "image/jpeg");
        assert_eq!(decode(&t).dimensions(), (200, 100));

        // Sizes are clamped.
        let big = thumbs.thumbnail(&photo, 5000).unwrap();
        assert_eq!(decode(&big).dimensions(), (MAX_SIZE, MAX_SIZE / 2));
    }

    #[test]
    fn transparent_images_stay_png() {
        let (_d, base, thumbs) = setup();
        let icon = base.join("icon.png");
        ImageBuffer::from_fn(300, 300, |_, _| Rgba([0u8, 0, 0, 0]))
            .save(&icon)
            .unwrap();
        let t = thumbs.thumbnail(&icon, 64).unwrap();
        assert_eq!(t.content_type, "image/png");
        assert!(decode(&t).color().has_alpha());
    }

    #[test]
    fn caches_until_the_file_changes() {
        let (_d, base, thumbs) = setup();
        let photo = base.join("p.png");
        write_png(&photo, 300, 300);
        let first = thumbs.thumbnail(&photo, 128).unwrap();
        assert_eq!(cached_files(&thumbs), 1);
        let again = thumbs.thumbnail(&photo, 128).unwrap();
        assert_eq!(first.bytes, again.bytes);
        assert_eq!(cached_files(&thumbs), 1);

        // A different size is a separate entry.
        thumbs.thumbnail(&photo, 64).unwrap();
        assert_eq!(cached_files(&thumbs), 2);

        // Replacing the image (new size and mtime) makes a new preview.
        write_png(&photo, 100, 50);
        let changed = thumbs.thumbnail(&photo, 128).unwrap();
        assert_eq!(decode(&changed).dimensions(), (100, 50));
    }

    #[test]
    fn works_without_a_cache() {
        let (_d, base, _) = setup();
        let thumbs = Thumbnailer::new(None, u64::MAX);
        let photo = base.join("p.png");
        write_png(&photo, 100, 100);
        assert!(thumbs.thumbnail(&photo, 64).is_ok());
    }

    #[test]
    fn rejects_what_it_cannot_preview() {
        let (_d, base, thumbs) = setup();
        let text = base.join("notes.txt");
        fs::write(&text, "hello").unwrap();
        let broken = base.join("broken.jpg");
        fs::write(&broken, "not really a jpeg").unwrap();
        let svg = base.join("logo.svg");
        fs::write(&svg, "<svg/>").unwrap();
        for path in [&text, &broken, &svg] {
            assert!(
                matches!(
                    thumbs.thumbnail(path, 64),
                    Err(LunarError::ThumbnailUnavailable(_))
                ),
                "{}",
                path.display()
            );
        }
        assert!(matches!(
            thumbs.thumbnail(&base, 64),
            Err(LunarError::IsADirectory)
        ));
        assert!(matches!(
            thumbs.thumbnail(&base.join("missing.png"), 64),
            Err(LunarError::NotFound)
        ));

        let tiny_limit = Thumbnailer::new(None, 10);
        let photo = base.join("p.png");
        write_png(&photo, 100, 100);
        assert!(matches!(
            tiny_limit.thumbnail(&photo, 64),
            Err(LunarError::ThumbnailUnavailable(_))
        ));
    }

    #[test]
    fn prune_removes_only_old_files() {
        let (_d, base, thumbs) = setup();
        let photo = base.join("p.png");
        write_png(&photo, 100, 100);
        thumbs.thumbnail(&photo, 64).unwrap();
        let old = thumbs.cache_dir().unwrap().join("old.jpg");
        fs::write(&old, "x").unwrap();
        let file = fs::File::options().write(true).open(&old).unwrap();
        file.set_modified(SystemTime::now() - CACHE_MAX_AGE - Duration::from_secs(60))
            .unwrap();
        thumbs.prune_cache();
        assert!(!old.exists());
        assert_eq!(cached_files(&thumbs), 1);
    }
}

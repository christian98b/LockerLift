# 16 KB Page-Size Compatibility Remediation

  - Upgraded the Compose BOM to `2025.08.01` and pinned `androidx.graphics:graphics-path` to `1.1.0` for the current native graphics artifact while retaining SDK 35 compatibility.
  - Upgraded `net.zetetic:sqlcipher-android` from `4.5.5` to `4.17.0`, the latest SDK 35-compatible release.
  - Verified AGP `8.7.2` already satisfies the required 16 KB APK packaging support.

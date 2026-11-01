# Debug build artifact

The current debug APK and its SHA-256, signature, package metadata and archive listing are generated under `deliverables/` on the GitHub Actions runner, then uploaded as `flip-preview-debug-apk`. Retrieve the artifact from the successful run for the desired commit:

https://github.com/8dia479/duo-flip-live/actions/workflows/flip-preview.yml

The local sandbox cannot reach GitHub Actions artifact storage, so the APK has not been copied into this workspace. This is an experimental in-app preview, not a verified Flip 6 system-transition release. See `docs/FLIP6-DEVICE-TEST.ko.md` before installation.

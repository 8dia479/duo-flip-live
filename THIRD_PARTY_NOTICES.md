# Third-party notices

This repository preserves the upstream notices in `app/src/main/assets/licenses/` and `upstream/`.

| Component | Source | License |
| --- | --- | --- |
| Upstream animation engine, originally derived from Duo Launcher | https://github.com/McCal-Codes/folio/tree/v0.6.0 | MIT; Duo Launcher contributors |
| iPhone Duo shader reference and adapted animation | https://github.com/chuspeeism/iphone-duo | MIT; jadon7 |
| FoldUO portions credited in the existing distribution | `app/src/main/assets/licenses/FOLDUO-MIT.txt` | MIT; bunkaich |
| Shizuku API and provider | https://github.com/RikkaApps/Shizuku-API | Apache-2.0 |
| HiddenApiBypass 6.1 | https://github.com/LSPosed/AndroidHiddenApiBypass | Apache-2.0 |
| AndroidX, Compose, Kotlin, coroutines, Gradle | See upstream notices and dependency graph | Apache-2.0 and their preserved upstream notices |

`reference/iphone-duo/main.js` preserves the MIT reference source used for the shader adaptation. No Samsung wallpaper APKs or videos, Apple device imagery, user photos, device logs, or signing keys are included. The application uses the Samsung engine already installed on the user's phone.

The upstream Folio notices describe Folio as it existed upstream. This standalone app uses only the included subset and adds Shizuku capture, custom wallpaper, and onboarding code. The current iPhone Duo adaptation is code-derived, rather than inspiration alone.

| Samsung hinge-sample interpolation design | https://github.com/OrangeHubX/duo-open-for-galaxy | MIT; marcoazeem (2026), adapted for the Duo Flip preview estimator |

The adapted interpolation design is based on the two-source sample-timing approach in `OrangeHubX/duo-open-for-galaxy`. Its original MIT notice is preserved at `app/src/main/assets/licenses/ORANGEHUBX-MIT.txt`. Its 10°/11° sample-step values were reported for a Galaxy Z Fold 8 Ultra and are treated as provisional, not as verified Galaxy Z Flip 6 specifications.

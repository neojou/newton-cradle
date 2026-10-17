package com.neojou.newtoncradle

/**
 * Application product version — single source of truth for UI / About.
 *
 * This file lives in `shared/src` and is compiled into `:composeApp`.
 * When bumping a release, update these constants first, then follow
 * [docs/VERSIONING.md](../../../../../docs/VERSIONING.md).
 *
 * Scheme (product-facing, not forced SemVer):
 * - [NAME]: `MAJOR.MINOR` (e.g. `"0.2"`) or `MAJOR.MINOR.PATCH` when needed
 * - [DISPLAY]: shown in About, typically `"v" + NAME`
 * - Gradle `version` is [NAME] plus `.0`
 */
object AppVersion {
    /** Product name (Traditional Chinese). */
    const val APP_NAME: String = "牛頓擺"

    /** English / package short name. */
    const val APP_NAME_EN: String = "Newton's Cradle"

    /**
     * Marketing / product version string (no leading `v`).
     * 0.2 is the kool rewrite. The old KDoc "0.11" did not match [NAME].
     */
    const val NAME: String = "0.2"

    /** User-visible label, e.g. `v0.2`. */
    const val DISPLAY: String = "v$NAME"

    /** One-line blurb for About. */
    const val SUMMARY: String = "五顆鋼球、彈性碰撞、PBR 產品照的牛頓擺"
}

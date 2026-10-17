# 版本號

產品版本只有一個來源：`shared/src/com/neojou/newtoncradle/AppVersion.kt`。

`:composeApp` 用 Gradle `kotlin.srcDir` 編譯這份檔案。About 畫面讀 `APP_NAME`、`APP_NAME_EN`、`DISPLAY`、`SUMMARY`。

| 欄位 | 意義 | 目前 |
|---|---|---|
| `AppVersion.NAME` | 產品版本，沒有前綴 `v` | `0.2` |
| `AppVersion.DISPLAY` | About 顯示的字串 | `v0.2` |
| 模組 Gradle `version` | 產物檔名用的版本，等於 `NAME` 加 `.0` | `0.2.0` |

改版時先改 `NAME`，再把 `:composeApp` 的 `version` 設成 `x.y.0`。不要在 Gradle 或畫面另外寫死版本。

`0.2` 表示改寫到 kool 引擎。`1.0` 留給產品照級畫面確認完成之後。舊 KDoc 寫過「0.11」，與當時的 `NAME = "0.1"` 不符，不再使用。

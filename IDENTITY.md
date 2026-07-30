# Kiwami — Identity & Third-Party IDs

Every hardcoded identifier that ties this app to a name, an account, or someone else's
server. Use this if you want to cut the last ties to ReDantotsu/Dantotsu and run Kiwami
fully on your own infrastructure.

Ordered by blast radius: things that break logins first, cosmetic last.

---

## 1. Third-party OAuth clients — NOT yours

(Discord was removed from the app entirely — see section 8.)

| What | Value | Where | Owner |
|---|---|---|---|
| AniList client ID | `47328` | [Anilist.kt](app/src/main/java/ani/dantotsu/connections/anilist/Anilist.kt) | **Kiwami** — ⚠️ not yet working |
| MAL client ID | `9bbbe2b1a595244587e1cc8a21048fde` | [MAL.kt:21](app/src/main/java/ani/dantotsu/connections/mal/MAL.kt#L21) | upstream — still to replace |

⚠️ **Client 47328 currently rejects every authorization request.** Tested against a live
AniList session, all three of these return the same error:

- `?client_id=47328&response_type=token`
- `?client_id=47328&response_type=code`
- `?client_id=47328&redirect_uri=redantotsu://auth&response_type=token`

```json
{"error":"unsupported_grant_type","message":"The authorization grant type is not supported
by the authorization server.","hint":"Check that all required parameters have been provided"}
```

Because it fails identically regardless of `response_type` and regardless of whether
`redirect_uri` is supplied, the cause is the client registration itself rather than the
request — most likely an empty **Redirect URL** field on the client at
<https://anilist.co/settings/developer>. Setting it to exactly `redantotsu://auth` is the
fix. Upstream client `35169` still works, so reverting the one line in `Anilist.kt` restores
login if needed.

**Do not add the AniList client secret to this repo or to the app.** The app uses the
implicit grant (`response_type=token`) and never exchanges a secret; a secret shipped in an
APK is readable by anyone who unzips it. There is deliberately no secret stored anywhere in
this project.

Both are OAuth clients tied to **their** registered redirect URIs. Changing the URL scheme
in section 2 without also replacing these IDs silently breaks login: the provider sends the
token to `redantotsu://…`, which nothing in your app will be listening for anymore.

Neither authorize call sends a `redirect_uri` parameter — AniList uses
`response_type=token` and MAL sends only `client_id` + `code_challenge`, so both providers
redirect to whatever URI is registered against the client ID. That is why you cannot change
the scheme unilaterally.

Neither uses a **client secret**: AniList is an implicit grant, MAL uses PKCE. There is no
`client_secret` anywhere in the codebase, so leave the secret out even though MAL issues
one.

### To take these over

1. **AniList** — create a client at <https://anilist.co/settings/developer>. Set its
   redirect URI to `<yourscheme>://auth`. Put the numeric ID in `Anilist.kt:249`.
2. **MAL** — create an app at <https://myanimelist.net/apiconfig>. Redirect URI
   `<yourscheme>://mal`. MAL uses PKCE here (`code_challenge`), so no client secret is
   needed — see [MAL.kt:37](app/src/main/java/ani/dantotsu/connections/mal/MAL.kt#L37).
3. **Discord** — create an app at <https://discord.com/developers/applications>. This one
   is only for Rich Presence artwork/name; the ID is what makes the presence card say
   your app's name. Also replace `small_Image` in `Discord.kt`, which is a
   Discord-CDN-hosted emoji asset whose URL literally contains `name%3DDantotsu`.

---

## 2. URL schemes & deep links

Changing these is what actually renames the app's identity to the OS. Do it **together
with** section 1, never alone.

| Scheme / host | Where |
|---|---|
| `redantotsu://auth` (AniList callback) | [AndroidManifest.xml:280](app/src/main/AndroidManifest.xml#L280) |
| `redantotsu://mal` (MAL callback) | [AndroidManifest.xml:297](app/src/main/AndroidManifest.xml#L297) |
| `redantotsu://` (Discord login) | [AndroidManifest.xml:312](app/src/main/AndroidManifest.xml#L312), [:330](app/src/main/AndroidManifest.xml#L330) |
| `redantotsu://` (CookieCatcher webview) | [AndroidManifest.xml:412](app/src/main/AndroidManifest.xml#L412) |
| `discord.redantotsu.com` | [AndroidManifest.xml:315](app/src/main/AndroidManifest.xml#L315), [:333](app/src/main/AndroidManifest.xml#L333) |

`discord.redantotsu.com` is a domain **you do not control**. It exists so an OAuth
redirect through a real https URL can be caught by the app. If you replace the Discord
client you should point this at your own domain or drop the `http`/`https` data entries and
rely on the custom scheme alone.

There are also four `android:label="… Login for Dantotsu"` intent-filter labels in the same
file. Purely cosmetic — they surface only in a disambiguation dialog.

---

## 3. Backend services — NOT yours

| Service | Endpoint | Default state | Where |
|---|---|---|---|
| Comments backend | `https://api.dantotsu.app` | **off** (`CommentsEnabled` = 0) | [CommentsAPI.kt:30](app/src/main/java/ani/dantotsu/connections/comments/CommentsAPI.kt#L30) |
| Download addon | GitHub `rebelonion/Dantotsu-Download-Addon` | on demand | [DownloadAddonManager.kt:133](app/src/main/java/ani/dantotsu/addons/download/DownloadAddonManager.kt#L133) |
| Torrent addon | GitHub `rebelonion/Dantotsu-Torrent-Addon` | on demand | [TorrentAddonManager.kt:140](app/src/main/java/ani/dantotsu/addons/torrent/TorrentAddonManager.kt#L140) |
| Privacy policy | `gcore.jsdelivr.net/gh/rebelonion/dantotsu/privacy_policy.md` | shown in About | [SettingsAboutActivity.kt:148](app/src/main/java/ani/dantotsu/settings/SettingsAboutActivity.kt#L148) |

The comments server authenticates by POSTing your **AniList** token to
`/authenticate`, so accounts, bans, and mod flags all live on rebelonion's server. It is
disabled by default: when off, `ADDRESS` resolves to `https://127.0.0.1` so no traffic
leaves the device. Replacing this means standing up your own compatible API.

The privacy policy URL still points at rebelonion's repo even though this repo has its own
[privacy_policy.md](privacy_policy.md) — worth repointing at `crimznexus/Kiwami`.

---

## 4. Storage & preference keys — changing these loses user data

| Key | Value | Consequence if changed |
|---|---|---|
| Preferences file | `dantotsuprefs` | **Wipes all settings and saved login tokens.** [strings.xml:4](app/src/main/res/values/strings.xml#L4) |
| Download root | `ReDantotsu` | Existing downloads become invisible. [DownloadsManager.kt:269](app/src/main/java/ani/dantotsu/download/DownloadsManager.kt#L269) |
| Legacy download paths | `ReDantotsu/{Anime,Manga,Novel}/…` | **Do not change.** [DownloadCompat.kt](app/src/main/java/ani/dantotsu/download/DownloadCompat.kt) |
| `applicationId` | `app.redantotsu.asr` | New app identity; installs side-by-side instead of upgrading, and orphans app-private storage. [build.gradle:31](app/build.gradle#L31) |

`DownloadCompat` is annotated `@Deprecated("external storage is deprecated, use SAF
instead")`. Its path strings are a historical record of folders written by older versions —
they are read-only migration data, not branding. Renaming them makes old downloads
unreadable.

If you do want to rename the download root, add a migration that moves the old directory
before switching `BASE_LOCATION`, rather than just editing the constant.

---

## 5. Repo & social links

| String | Current value | Effect |
|---|---|---|
| `repo` | `crimznexus/Kiwami` | Drives the GitHub contributors call and (google flavor only) the update checker |
| `github` | `https://github.com/crimznexus/Kiwami` | About screen link |
| `discord` | `https://discord.gg/fYEJmDsDz9` | **ReDantotsu's server** |
| `telegram` | `https://t.me/redantotsu` | **ReDantotsu's channel** |
| `coffee` | `https://patreon.com/AsrOfficialDev` | **ReDantotsu's Patreon** |
| `dantotsu` | `https://dantotsu.app/` | Original project's site |

All in [strings.xml:3-16](app/src/main/res/values/strings.xml#L3-L16). The bottom three are
live buttons on the logged-out home screen — as shipped they send your users to someone
else's community and fund someone else's Patreon.

---

## 6. Deliberately left as-is (internal, no user impact)

Renaming these is a large mechanical refactor with zero visible benefit:

- Kotlin package / `namespace`: `ani.dantotsu` — appears in ~488 source files.
- Theme names: `Theme.Dantotsu`, `Theme.Dantotsu.NoActionBar`, `Theme.Dantotsu.NeverCutout`.
- Resource IDs: `stream_on_dantotsu`, `read_on_dantotsu`, `redantotsu_section`,
  `homeDantotsuIcon`, etc. The **values** are rebranded; only the ID strings still say
  Dantotsu.
- `redantotsu_section` and the `"ReDantotsu Developer"` credit in
  [Contributors.kt:26](app/src/main/java/ani/dantotsu/connections/github/Contributors.kt#L26)
  are accurate attribution of AsrOfficialDev's work and should stay. The UPL/GPLv3 license
  requires preserving copyright notices.

---

## 7. Firebase (google flavor only)

`app/google-services.json` is gitignored and absent. The `google` flavor needs one whose
`android_client_info.package_name` matches `applicationId` exactly — the committed
[google-services.json.example](app/google-services.json.example) says `ani.dantotsu`, which
is stale and will fail with "No matching client found". The `fdroid` flavor needs none and
uses a no-op `CrashlyticsFactory`.

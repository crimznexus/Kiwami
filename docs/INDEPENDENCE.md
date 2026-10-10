# Making Kiwami independent

Work through these in order. `[x]` = done, `[ ]` = to do. Items marked **(needs you)** need an account, key or decision only you can supply.

## 1. Own sign-in apps  <- current step
- [ ] **AniList** — confirm client `47328` is registered under your own AniList account (anilist.co/settings/developer). **(needs you)**
- [ ] **AniList redirect** — change it to `kiwami://auth` on that page. **(needs you)**
- [ ] **MAL** — create your own app at myanimelist.net/apiconfig (type: other, redirect `kiwami://mal`) and send me the Client ID. **(needs you)**
- [ ] Switch the app to the `kiwami://` scheme (manifest, MAL client ID) and test both logins.

## 2. Remove upstream servers
- [ ] Comments feature (`api.dantotsu.app`) — remove, or point at your own server.
- [ ] Torrent / download add-ons (`rebelonion/Dantotsu-*-Addon`) — fork them under your account, or hide the feature.
- [ ] Payment-success intent filter and Patreon / Dantotsu links.

## 3. Rename leftovers
- [ ] Downloads folder `ReDantotsu/` -> `Kiwami/`, with a migration so existing downloads are kept.
- [ ] Dialog labels in the manifest ("Login for Dantotsu").
- [ ] Privacy policy, FAQ and About links -> your own pages.

## 4. Legal and credits
- [ ] Keep GPL-3.0, the public source, and the Dantotsu / ReDantotsu credits.
- [ ] Write Kiwami's own privacy policy (no accounts, tokens stay on device, no analytics).
- [ ] Check bundled licences (Real-ESRGAN, ncnn — already included).

## 5. Publishing
- [ ] **Back up the release key** (`kiwami-release.jks` and its properties file). **(needs you)**
- [ ] Decide channels: GitHub Releases (live), IzzyOnDroid, own F-Droid repo. Google Play is unlikely to accept extension scraping.
- [ ] Landing page / README polish, issue templates, `SECURITY.md`.

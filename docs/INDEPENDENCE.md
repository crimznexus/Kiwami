# Making Kiwami independent

Work through these in order. `[x]` = done, `[ ]` = to do. Items marked **(needs you)** need an account, key or decision only you can supply.

## 1. Own sign-in apps  <- current step
- [x] **AniList** — client `47328` is yours.
- [x] **AniList redirect** — after updating to 1.0.6, change it to `kiwami://auth` on that page. **(needs you)**
- [x] **MAL** — own app, client ID `2b82e1...` (redirect `kiwami://mal`).
- [x] App accepts both `kiwami://` and `redantotsu://` for AniList and MAL login (done, resolves on the dev build).

## 2. Remove upstream servers
- [x] Comments feature (`api.dantotsu.app`) — switched off for good (setting hidden).
- [x] Torrent / download add-ons (`rebelonion/Dantotsu-*-Addon`) — hidden from Settings.
- [x] Payment-success intent filter and Patreon and Telegram buttons hidden.

## 3. Rename leftovers
- [x] Downloads folder `ReDantotsu/` -> `Kiwami/`, with a migration so existing downloads are kept.
- [x] Dialog labels in the manifest ("Login for Dantotsu").
- [x] Privacy policy, FAQ and About links -> your own pages.

## 4. Legal and credits
- [x] Keep GPL-3.0, the public source, and the Dantotsu / ReDantotsu credits.
- [x] Write Kiwami's own privacy policy (no accounts, tokens stay on device, no analytics).
- [x] Check bundled licences (Real-ESRGAN, ncnn — already included).

## 5. Publishing
- [ ] **Back up the release key** (`kiwami-release.jks` and its properties file). **(needs you)**
- [ ] Decide channels: GitHub Releases (live), IzzyOnDroid, own F-Droid repo. Google Play is unlikely to accept extension scraping.
- [x] Issue templates, `SECURITY.md` (landing page still optional)  # / README polish, issue templates, `SECURITY.md`.

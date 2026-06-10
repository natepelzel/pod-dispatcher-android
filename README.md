# Pod Dispatcher

Open a podcast link from anywhere — Apple Podcasts, Spotify, a friend's
message — in **your** podcast app.

Pod Dispatcher is a small Android app (iOS planned) that intercepts podcast
share links, resolves them to the canonical RSS feed, and forwards them to
your preferred player. There is no UI to speak of: tap a link, land in your
app.

## How it works

The native code is a static **parser/redirector engine**. Everything
app-specific lives in declarative YAML schemas:

```
incoming URL ──▶ source schema ──▶ canonical identity ──▶ target schema ──▶ deep link
  (podcasts.       (URL patterns +     (RSS feed URL,        (link templates)    (your app)
   apple.com)       resolver)           episode info)
```

* **One schema file per app/platform**, maintained in the shared
  [pod-dispatcher-schemas](https://github.com/natepelzel/pod-dispatcher-schemas)
  repository and mounted here as the [`schemas/`](schemas/) git submodule so
  the Android and (planned) iOS apps consume the same files. A schema can
  describe how to *parse* a platform's links (`source`), how to *deep link*
  into an app (`target`), or both.
* Schemas are **purely declarative** — regex patterns and string templates,
  no executable code. They're validated in CI against a
  [JSON Schema](schemas/pod-dispatcher.schema.json) and can be updated
  over-the-air without an app release.
* Coverage grows by community contribution: add a YAML file, open a PR
  against the schemas repo — **that's the only change needed**. The Android
  manifest (intercepted hosts, `<queries>` packages) is generated from the
  schemas at build time by `:app:generateManifest`. See
  [schemas/SCHEMA.md](schemas/SCHEMA.md) for the full format reference and
  contribution checklist.

Currently shipped schemas:

| Schema | Role | Notes |
| ------ | ---- | ----- |
| [`apple-podcasts`](schemas/apple-podcasts.yml) | source + target | show + episode links via the iTunes Lookup API; target is iOS-only |
| [`spotify`](schemas/spotify.yml) | source + target | both directions resolved through the Podnews directory |
| [`pocket-casts`](schemas/pocket-casts.yml) | source + target | `pca.st/itunes/…` in, `pktc://subscribe/…` out |
| [`podcast-addict`](schemas/podcast-addict.yml) | source + target | show + **episode** share pages in; show + episode deep links out |
| [`overcast`](schemas/overcast.yml) | source + target | target is iOS-only (no Android package) |
| [`castro`](schemas/castro.yml) | source + target | target is iOS-only |
| [`castbox`](schemas/castbox.yml) | source + target | Apple-id-keyed links |
| [`antennapod`](schemas/antennapod.yml) | source + target | subscribe deeplinks both ways (feed-carrying) |
| [`subscribe-on-android`](schemas/subscribe-on-android.yml) | source | publisher subscribe links that carry the feed URL |
| [`youtube-music`](schemas/youtube-music.yml) | target | base64url feed deep link |
| [`player-fm`](schemas/player-fm.yml) | target | feed-keyed subscribe link |
| [`podbean`](schemas/podbean.yml) | target | Apple-id-keyed |
| [`podcast-republic`](schemas/podcast-republic.yml) | target | Apple-id-keyed |
| [`podcast-guru`](schemas/podcast-guru.yml) | target | Apple-id-keyed |

## Building

Requires JDK 17 and the Android SDK. The schemas live in a git submodule, so
clone with `--recurse-submodules` (or run `git submodule update --init` in an
existing checkout).

```bash
./gradlew :app:assembleDebug                # build
./gradlew :app:testDebugUnitTest            # run the engine's unit tests
python schemas/tools/validate_schemas.py    # validate schema files (pip install -r schemas/tools/requirements.txt)
```

## Project layout

```
app/        Android app (Kotlin) — intent handler + schema engine
schemas/    git submodule → pod-dispatcher-schemas: one YAML schema per
            app/platform (bundled as assets, served OTA), the JSON Schema
            format definition, docs, and the CI validator
```

Engine code worth knowing about:

* `engine/UrlMatcher.kt` — host + regex pattern matching with named captures
* `engine/resolve/` — `itunes-api`, `scrape`, `podcast-index` resolvers
* `engine/DeepLinkBuilder.kt` — target templates with episode→show fallback
* `engine/SchemaRepository.kt` — bundled assets + OTA refresh
* `ui/DispatchActivity.kt` — the invisible link-handling entry point

## Android platform limitations (honesty corner)

* **Static hosts.** Android intent filters are baked into the APK, so the set
  of *intercepted* hostnames is fixed per release. The filters are generated
  from the schemas at build time (no manual XML, ever), but a schema with a
  brand-new host is only intercepted from the next release; OTA schema
  updates can change how known hosts are parsed. The escape hatch: **sharing**
  a link to Pod Dispatcher works for any host, so an OTA-delivered source is
  usable via the share sheet right away — automatic interception follows with
  the next release.
* **Link verification.** We can't pass app-link verification for domains we
  don't own, so on Android 12+ users must enable the supported links manually
  (Settings → Apps → Pod Dispatcher → Open by default). The app's main screen
  has a shortcut to that page.
* **Package visibility.** The manifest's `<queries>` block (Android 11+) is
  generated from the schemas' target packages. Launching is unaffected
  (`startActivity` doesn't require visibility), so an OTA-added target works
  immediately — but the picker can only *detect* it as installed from the
  next release.

## Roadmap

* More source schemas (Spotify, Overcast, Castro, Google/YouTube Music) and
  target schemas (AntennaPod, Overcast, Castro, …)
* Podcast Index resolver (needs API credentials wired into the build)
* Episode-level resolution beyond show fallback (RSS GUID matching)
* iOS app sharing the same schema repository

## Support

Pod Dispatcher is free and open source, with no ads or tracking. If it saved
you a few taps, [buy me a coffee](https://buymeacoffee.com/natepelzel) ☕

## License

[Apache-2.0](LICENSE)

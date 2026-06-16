# Releasing Create: NUE

**Every release goes to GitHub *and* Modrinth together — keep them in sync.**

1. **Bump** `mod_version` in `gradle.properties` and add a `CHANGELOG.md` entry.
2. **Build** — `./gradlew build` (needs Create, Flowing Fluids, CC: Tweaked, and `fabric-api-lookup-api-v1`
   jars in `libs/`). Output: `build/libs/create-nue-<version>.jar`.
3. **GitHub** — commit + push, then cut a **Release** with the jar attached:
   ```
   git add -A && git commit -m "vX.Y.Z" && git push
   gh release create vX.Y.Z build/libs/create-nue-X.Y.Z.jar \
     --title "vX.Y.Z" --notes-file <(the new CHANGELOG section)
   ```
   (repo: <https://github.com/plagness/Create-No-Unlimited-Energy/releases>). The tag is created by `gh`.
4. **Modrinth** — project `create-nue` (id `VIpZhucq`). Upload the **same** jar as a new version:
   - Game versions: **1.20.1 only** (the mod targets Create 6.0.8.1 / FF 1.0.6 on 1.20.1).
   - Loader: **Fabric** · channel **Release** · changelog from `CHANGELOG.md`.
   - Dependencies: **Create Fabric** (required), **Flowing Fluids** (required), **CC: Tweaked** (optional).
   - Can be done via the Modrinth API (`POST /v2/version`) with a PAT, or the web UI.

Keep the in-repo README and the Modrinth description in sync.

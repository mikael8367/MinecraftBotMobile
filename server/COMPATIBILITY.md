# MinecraftBotMobile — backend compatibility and review notes

## Engines

- `official`: the installed `mineflayer` package from PrismarineJS.
- `mikael`: a project-owned adapter in `server/motors/mikael.js` around the MIT-licensed `@wp2508/mineflayer` fork, whose package contains protocol changes for Minecraft 26.x. This adapter is **not** an independent reimplementation of Minecraft's protocol; it validates versions and normalizes the app's integration.

The list exposed by `GET /api/capabilities` comes from each installed package's `lib/version.js` `testedVersions` metadata. It does not mean MinecraftBotMobile has tested every version against a live server.

## Important compatibility notes

- The upstream PrismarineJS Mineflayer matrix currently includes versions up through 26.1; the selected fork lists 26.1.2, 26.2 and 26.3 as well.
- `mineflayer-pathfinder` is a separate dependency. This backend deliberately does not load it when a 26.x version is explicitly selected. For an Auto connection, follow is disabled after spawn if Mineflayer reports a 26.x server version.
- Movement, world parsing and other protocol behavior still require tests against an actual server for the selected version.
- Offline mode only works on servers that permit offline/cracked connections. It does not authenticate a Microsoft account.
- Use HTTPS and a strong `API_TOKEN` in Render. The token protects bot control endpoints; do not publish it in code, logs, or screenshots.

## Checks

Run `npm test` in the `server` directory for the request-validation regression tests. Android compilation is handled by the repository's GitHub Actions workflow. A green build is not proof of a successful live Minecraft login.

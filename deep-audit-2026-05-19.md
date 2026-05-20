# Deep Audit Snapshot - Tebex (forge-1.18.2 multi-shop changes)

- **Scope**: changes (multi-shop key feature on forge-1.18.2)
- **Date**: 2026-05-19
- **SHA**: 1e068b4 (uncommitted working tree on top)
- **Verdict**: 🟡 YELLOW

Snapshot from one audit pass. Findings reflect the code as of the SHA above; line numbers and severities will drift as fixes land. Do not treat this as a living spec - re-run `/deep-audit` for a current view.

## CRITICAL
None.

## HIGH

### Race conditions / threading

- [ ] [forge-1.18.2/src/main/java/com/github/xniter/tebexio/TebexForged.java:228-244] `scheduledTasks` (ArrayList) mutated from the executor thread inside `initializeShop`, iterated from the server tick thread in `onTick`.
  why: `SecretCmd` submits `addOrUpdateShop` to `getExecutor()`. For a new shop, that calls `initializeShop` which does `scheduledTasks.add(...)` four times. Tick thread can be inside `scheduledTasks.forEach(...)` simultaneously -> `ConcurrentModificationException`. First `/tebex secret <newshop> <key>` on a live server triggers it.

- [ ] [forge-1.18.2/src/main/java/com/github/xniter/tebexio/TebexForged.java:246, 274, 322] `shops` (LinkedHashMap) mutated from executor thread (SecretCmd) and server thread (RemoveShopCmd), iterated from server/network thread in `onPlayerJoin` and from various command threads.
  why: Player login while another admin runs `/tebex secret` can throw `ConcurrentModificationException` mid-iteration of `shops.values()` in `onPlayerJoin`. Same hazard for `ReportCmd`, `BuyCommand`, `ShopsCmd`. LinkedHashMap is not thread-safe.

### Resource leaks

- [ ] [forge-1.18.2/src/main/java/com/github/xniter/tebexio/TebexForged.java:232-244, 273-286] `removeShop` does not stop the per-shop analytics task (anonymous lambda, no reference held) nor the `DuePlayerFetcher` (self-reschedules via `platform.executeAsyncLater(this, ...)` at common/.../DuePlayerFetcher.java:101).
  why: After `/tebex remove owner1`, the analytics lambda continues posting once per 24h with the removed shop's stored key. The fetcher continues polling Tebex every ~300s with the removed shop's stored API client. Both run until server restart. The shop is gone from the map but the network chatter and credential use continue.

## MEDIUM

- [ ] [forge-1.18.2/.../TebexForged.java:172-193, 288-294] Legacy `server-key=` entry persists in `config.properties` after `/tebex secret default newkey` migrates it to `server-key.default=`. Functionally ignored but visually confusing.
- [ ] [forge-1.18.2/.../command/SecretCmd.java:33-44, TebexForged.java:200-215] Brand-new shop validates secret key twice (once in SecretCmd, again in `initializeShop`). Wasted API call, duplicate log line.
- [ ] [forge-1.18.2/.../TebexForged.java:147-162] `VersionCheck` is bound to one shop's key at startup; if that shop is later removed at runtime the check is dead. Harmless until next startup with no shops, where update checks silently disable.
- [ ] [forge-1.18.2/.../TebexForged.java:296-316] No `SuggestionProvider` on the `<shop>` brigadier argument - admins get no tab-completion of configured shop names.
- [ ] [forge-1.18.2/.../command/BuyCommand.java:35-54] `/buy` UX is confusing when shops exist but none have `serverInformation` (e.g. all keys failed validation): prints decoration + `information_no_server` with no clear cause.
- [ ] [forge-1.18.2/.../TebexForged.java:249-271, command/SecretCmd.java:48-54] New-shop creation triggers two `DuePlayerFetcher.run()` invocations in quick succession (the scheduled 1s-delay one from `initializeShop`, and the explicit one in `SecretCmd`). `inProgress` AtomicBoolean prevents overlap, but architecture is doing double work.
- [ ] [forge-1.18.2/.../TebexForged.java:78] Fresh-setup log message references `'tebex secret <key>'` (old single-arg form); should be `'tebex secret <shop> <key>'`.

## Recommended next actions

1. Wrap `scheduledTasks` with `CopyOnWriteArrayList` or move all mutation to the server thread via `server.execute(...)`. Fixes H1.
2. Wrap `shops` with `ConcurrentHashMap`, or `Collections.synchronizedMap(new LinkedHashMap<>())` with locked iteration, or constrain all mutation to the server thread. Fixes H2.
3. Have `TebexShop` hold the analytics `ForgeScheduledTask` and the `ScheduledFuture` returned by `executeAsyncLater(duePlayerFetcher, ...)`; `removeShop` cancels the future and nulls `apiClient` so any still-in-flight fetcher exits early. Fixes H3.
4. On startup, if `discoverShopKeys` populates `default` from legacy `server-key=`, immediately rewrite the file (`remove("server-key")`, `setProperty("server-key.default", ...)`, save). One-shot migration.
5. Add a `SuggestionProvider` on the `<shop>` argument that suggests `plugin.getShops().stream().map(TebexShop::getName)`.

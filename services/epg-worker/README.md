# Premium TV Player — EPG Worker

Standalone Node process that fetches XMLTV from every active
`xmltv` / `m3u_plus_epg` source, parses it via
`@premium-player/parsers`, and upserts `epg_channels` + `epg_programs`
so the API's `/v1/epg/*` endpoints serve live data to the Android TV
client's `EpgBrowseScreen`.

## Architecture

- Boots a headless Nest application context (`createApplicationContext`,
  no HTTP listener).
- Imports the API's `config`, `prisma`, and `sources` modules via the
  TS path alias `@api/*` — exactly one implementation of the
  persistence + decryption layer, shared between the API, the
  billing-worker, and this worker.
- Uses `@premium-player/parsers` (the same TS package consumed by the
  API's source-create preview stub in Run 10) so channel/programme
  extraction lives in one place.

## Commands

From `services/epg-worker/`:

```bash
npm install
npm run prisma:generate   # regenerates against ../api/prisma/schema.prisma
npm run start:dev         # dev: ts-node-dev watch
npm run build             # tsc → dist
WORKER_RUN_ONCE=true npm start   # one tick then exit (CI / on-demand)
npm start                 # poll loop
npm test                  # unit tests
```

## Env

| Key | Default | Purpose |
|---|---|---|
| `EPG_WORKER_POLL_INTERVAL_MS` | `1800000` (30 min) | Gap between polls |
| `EPG_WINDOW_AHEAD_HOURS` | `48` | Only persist programmes whose `starts_at` lies within `[now-1h, now+N hours]` |
| `WORKER_RUN_ONCE` | *(unset)* | Set `true` to exit after first tick |
| *(all other API envs)* | — | Same as `services/api/.env.example` |

## How a tick works

1. `SELECT * FROM sources WHERE is_active AND deleted_at IS NULL AND kind IN ('xmltv','m3u_plus_epg')`
2. For each row:
   1. Decrypt `source_credentials` via `SourceCryptoService`
   2. Fetch the URL (optional custom headers from the encrypted blob)
   3. Parse via `parseXmltv(...)`
   4. Upsert `epg_channels` (unique on `(sourceId, externalChannelId)`)
   5. Insert `epg_programs` filtered to the configured window
   6. Update the source row's `last_validated_at` + `validation_status=valid`

Per-source failures are logged but never abort the batch.

## Tests

`src/epg.worker.spec.ts` uses a Prisma/Source mock set + a fake
`EpgFetcher` transport so the full fetch → parse → persist round-trip
is exercised without a real network or database. Four cases:

- Run-once ticks through active sources, persists channels + programmes
- Programmes outside the window are skipped
- Empty XMLTV still marks the source `valid`
- Per-source failure isolation (one bad source doesn't kill the batch)

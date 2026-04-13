/**
 * M3U / M3U8 playlist parser (V1 stub).
 *
 * Scope: extract `#EXTINF` channel entries with their normalized metadata
 * (group, tvg-id/name, logo, channel name, stream URL). Sufficient for the
 * Run 10 source-creation flow which needs to estimate item counts and
 * surface basic channel info to the UI.
 *
 * Not in scope (deferred to Run 15+ EPG worker / source UI):
 *   - HLS variant playlists (#EXT-X-STREAM-INF / #EXT-X-MEDIA)
 *   - XMLTV reconciliation (handled by `xmltv.ts`)
 *   - chunk/segment manifests
 *
 * Input convention: full file body as UTF-8 string.
 */

export interface M3UChannel {
  /** `tvg-id` attribute, used to join with XMLTV epg_channels. */
  tvgId: string | null;
  /** `tvg-name` attribute, often the canonical channel slug. */
  tvgName: string | null;
  /** `tvg-logo` attribute, typically a URL. */
  logo: string | null;
  /** `group-title` attribute, e.g. "Sport" / "News". */
  group: string | null;
  /** Display name (free-form text after the comma in `#EXTINF`). */
  name: string;
  /** Duration in seconds; -1 means live stream (per spec). */
  durationSeconds: number;
  /** Direct stream URL (line right after `#EXTINF`). */
  url: string;
  /** Raw attribute map for lossless round-trip / future expansion. */
  attributes: Record<string, string>;
}

export interface M3UParseResult {
  channels: M3UChannel[];
  /** Raw lines that we skipped (e.g. comments other than #EXTINF). */
  ignoredLines: number;
  /** Lines that looked like `#EXTINF` but were malformed. */
  malformedEntries: number;
}

const EXTINF_PREFIX = '#EXTINF:';
const ATTRIBUTE_REGEX = /([A-Za-z0-9-_]+)="([^"]*)"/g;

export function parseM3U(input: string): M3UParseResult {
  const lines = input.split(/\r?\n/);
  const channels: M3UChannel[] = [];
  let ignoredLines = 0;
  let malformedEntries = 0;

  // Header sanity check (best-effort — tolerant of missing #EXTM3U).
  let i = 0;
  if (lines[0]?.startsWith('#EXTM3U')) i = 1;

  for (; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line === '' || (line.startsWith('#') && !line.startsWith(EXTINF_PREFIX))) {
      if (line !== '') ignoredLines++;
      continue;
    }
    if (!line.startsWith(EXTINF_PREFIX)) {
      // Stray non-comment line without preceding #EXTINF — ignore.
      ignoredLines++;
      continue;
    }

    const meta = parseExtinf(line);
    // Find the next non-empty, non-comment line for the URL.
    let urlLine: string | null = null;
    while (++i < lines.length) {
      const candidate = lines[i].trim();
      if (candidate === '' || candidate.startsWith('#')) {
        if (candidate !== '') ignoredLines++;
        continue;
      }
      urlLine = candidate;
      break;
    }
    if (!urlLine || !meta) {
      malformedEntries++;
      continue;
    }
    channels.push({
      ...meta,
      url: urlLine,
    });
  }

  return { channels, ignoredLines, malformedEntries };
}

function parseExtinf(line: string): Omit<M3UChannel, 'url'> | null {
  // Format: #EXTINF:<duration> [k="v" ...],<name>
  const body = line.substring(EXTINF_PREFIX.length);
  const commaIdx = body.indexOf(',');
  if (commaIdx === -1) return null;

  const head = body.slice(0, commaIdx).trim();
  const name = body.slice(commaIdx + 1).trim();

  // Duration is the first whitespace-separated token in `head`.
  const spaceIdx = head.indexOf(' ');
  const durationStr = (spaceIdx === -1 ? head : head.slice(0, spaceIdx)).trim();
  const durationSeconds = Number.parseFloat(durationStr);
  if (!Number.isFinite(durationSeconds)) return null;

  const attrSegment = spaceIdx === -1 ? '' : head.slice(spaceIdx + 1);
  const attributes = extractAttributes(attrSegment);

  return {
    name,
    durationSeconds,
    tvgId: attributes['tvg-id'] ?? null,
    tvgName: attributes['tvg-name'] ?? null,
    logo: attributes['tvg-logo'] ?? null,
    group: attributes['group-title'] ?? null,
    attributes,
  };
}

function extractAttributes(segment: string): Record<string, string> {
  const out: Record<string, string> = {};
  let m: RegExpExecArray | null;
  ATTRIBUTE_REGEX.lastIndex = 0;
  while ((m = ATTRIBUTE_REGEX.exec(segment)) !== null) {
    out[m[1]] = m[2];
  }
  return out;
}

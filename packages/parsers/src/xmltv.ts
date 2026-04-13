/**
 * XMLTV parser (V1 stub).
 *
 * Scope: extract `<channel>` and `<programme>` rows from a small-to-medium
 * XMLTV document. Pure-string XML walker — no external dependency. Good
 * enough for the Run 10 source-creation flow (basic validation + item
 * count estimate). Production-grade parsing with streaming + DTD handling
 * lands with the EPG worker (Run 15+).
 *
 * Input convention: full XMLTV string.
 */

export interface XmltvChannel {
  id: string;
  displayName: string | null;
  iconUrl: string | null;
}

export interface XmltvProgramme {
  channelId: string;
  /** UTC ISO timestamp (best-effort conversion from XMLTV time format). */
  startsAt: string | null;
  endsAt: string | null;
  title: string | null;
  subtitle: string | null;
  description: string | null;
  category: string | null;
}

export interface XmltvParseResult {
  channels: XmltvChannel[];
  programmes: XmltvProgramme[];
  malformedChannels: number;
  malformedProgrammes: number;
}

const CHANNEL_TAG_REGEX = /<channel\b([^>]*)>([\s\S]*?)<\/channel>/g;
const PROGRAMME_TAG_REGEX = /<programme\b([^>]*)>([\s\S]*?)<\/programme>/g;
const PROGRAMME_SELF_CLOSING_REGEX = /<programme\b([^>]*)\/>/g;
const ATTRIBUTE_REGEX = /([A-Za-z0-9_:-]+)="([^"]*)"/g;
const TAG_REGEX = (name: string): RegExp =>
  new RegExp(`<${name}\\b[^>]*>([\\s\\S]*?)<\\/${name}>`, 'i');

export function parseXmltv(input: string): XmltvParseResult {
  const channels: XmltvChannel[] = [];
  const programmes: XmltvProgramme[] = [];
  let malformedChannels = 0;
  let malformedProgrammes = 0;

  let m: RegExpExecArray | null;

  CHANNEL_TAG_REGEX.lastIndex = 0;
  while ((m = CHANNEL_TAG_REGEX.exec(input)) !== null) {
    const attrs = extractAttributes(m[1]);
    const inner = m[2];
    const id = attrs['id'];
    if (!id) {
      malformedChannels++;
      continue;
    }
    const dn = TAG_REGEX('display-name').exec(inner);
    const icon = /<icon\b[^>]*src="([^"]+)"/i.exec(inner);
    channels.push({
      id,
      displayName: dn ? decodeXml(dn[1].trim()) : null,
      iconUrl: icon ? icon[1] : null,
    });
  }

  PROGRAMME_TAG_REGEX.lastIndex = 0;
  while ((m = PROGRAMME_TAG_REGEX.exec(input)) !== null) {
    pushProgramme(m, programmes, () => malformedProgrammes++);
  }

  PROGRAMME_SELF_CLOSING_REGEX.lastIndex = 0;
  while ((m = PROGRAMME_SELF_CLOSING_REGEX.exec(input)) !== null) {
    const attrs = extractAttributes(m[1]);
    const channelId = attrs['channel'];
    if (!channelId) {
      malformedProgrammes++;
      continue;
    }
    programmes.push({
      channelId,
      startsAt: parseXmltvTime(attrs['start']) ?? null,
      endsAt: parseXmltvTime(attrs['stop']) ?? null,
      title: null,
      subtitle: null,
      description: null,
      category: null,
    });
  }

  return { channels, programmes, malformedChannels, malformedProgrammes };
}

function pushProgramme(
  m: RegExpExecArray,
  out: XmltvProgramme[],
  onMalformed: () => void,
): void {
  const attrs = extractAttributes(m[1]);
  const inner = m[2];
  const channelId = attrs['channel'];
  if (!channelId) {
    onMalformed();
    return;
  }
  out.push({
    channelId,
    startsAt: parseXmltvTime(attrs['start']) ?? null,
    endsAt: parseXmltvTime(attrs['stop']) ?? null,
    title: extractTagText(inner, 'title'),
    subtitle: extractTagText(inner, 'sub-title'),
    description: extractTagText(inner, 'desc'),
    category: extractTagText(inner, 'category'),
  });
}

function extractTagText(input: string, name: string): string | null {
  const m = TAG_REGEX(name).exec(input);
  if (!m) return null;
  return decodeXml(m[1].trim());
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

/**
 * XMLTV time format: `YYYYMMDDHHmmss [+|-]HHMM` (offset optional).
 * Returns ISO-8601 UTC string or null if unparseable.
 */
export function parseXmltvTime(raw: string | undefined): string | null {
  if (!raw) return null;
  const m = /^(\d{14})\s*([+-]\d{4})?$/.exec(raw.trim());
  if (!m) return null;
  const [, ts, tz] = m;
  const yyyy = ts.slice(0, 4);
  const mm = ts.slice(4, 6);
  const dd = ts.slice(6, 8);
  const HH = ts.slice(8, 10);
  const MM = ts.slice(10, 12);
  const SS = ts.slice(12, 14);
  const iso = `${yyyy}-${mm}-${dd}T${HH}:${MM}:${SS}${tz ? formatOffset(tz) : 'Z'}`;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

function formatOffset(tz: string): string {
  return `${tz.slice(0, 3)}:${tz.slice(3)}`;
}

function decodeXml(s: string): string {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

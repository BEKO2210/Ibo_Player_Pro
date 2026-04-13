import { parseXmltv, parseXmltvTime } from './xmltv';

describe('parseXmltv', () => {
  it('extracts channels and programmes from a small fixture', () => {
    const input = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="bbcone.uk">
    <display-name>BBC One</display-name>
    <icon src="https://logos/bbc1.png"/>
  </channel>
  <channel id="zdf.de">
    <display-name>ZDF</display-name>
  </channel>
  <programme channel="bbcone.uk" start="20260413180000 +0000" stop="20260413190000 +0000">
    <title>News at Six</title>
    <desc>The latest UK and world headlines.</desc>
    <category>News</category>
  </programme>
  <programme channel="zdf.de" start="20260413190000 +0200" stop="20260413200000 +0200">
    <title>heute-journal</title>
    <sub-title>Spezial</sub-title>
  </programme>
</tv>`;

    const r = parseXmltv(input);

    expect(r.channels).toHaveLength(2);
    expect(r.channels[0]).toEqual({
      id: 'bbcone.uk',
      displayName: 'BBC One',
      iconUrl: 'https://logos/bbc1.png',
    });
    expect(r.channels[1]).toEqual({
      id: 'zdf.de',
      displayName: 'ZDF',
      iconUrl: null,
    });

    expect(r.programmes).toHaveLength(2);
    expect(r.programmes[0]).toMatchObject({
      channelId: 'bbcone.uk',
      title: 'News at Six',
      description: 'The latest UK and world headlines.',
      category: 'News',
    });
    expect(r.programmes[0].startsAt).toBe('2026-04-13T18:00:00.000Z');
    expect(r.programmes[0].endsAt).toBe('2026-04-13T19:00:00.000Z');

    expect(r.programmes[1].subtitle).toBe('Spezial');
    // 19:00+02:00 -> 17:00 UTC
    expect(r.programmes[1].startsAt).toBe('2026-04-13T17:00:00.000Z');
  });

  it('handles self-closing programme elements', () => {
    const input = `<tv>
      <channel id="x"><display-name>X</display-name></channel>
      <programme channel="x" start="20260101000000 +0000" stop="20260101010000 +0000"/>
    </tv>`;
    const r = parseXmltv(input);
    expect(r.programmes).toHaveLength(1);
    expect(r.programmes[0].title).toBeNull();
    expect(r.programmes[0].endsAt).toBe('2026-01-01T01:00:00.000Z');
  });

  it('decodes XML entities in text content', () => {
    const input = `<tv>
      <channel id="x"><display-name>News &amp; Politics</display-name></channel>
    </tv>`;
    const r = parseXmltv(input);
    expect(r.channels[0].displayName).toBe('News & Politics');
  });
});

describe('parseXmltvTime', () => {
  it('returns null on undefined / malformed input', () => {
    expect(parseXmltvTime(undefined)).toBeNull();
    expect(parseXmltvTime('abc')).toBeNull();
    expect(parseXmltvTime('2026-04-13')).toBeNull();
  });

  it('parses with explicit offset', () => {
    expect(parseXmltvTime('20260413120000 +0530')).toBe('2026-04-13T06:30:00.000Z');
  });

  it('parses without offset (treats as UTC)', () => {
    expect(parseXmltvTime('20260413120000')).toBe('2026-04-13T12:00:00.000Z');
  });
});

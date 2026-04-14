import { parseM3U } from './m3u';

describe('parseM3U', () => {
  it('parses a typical multi-channel playlist', () => {
    const input = `#EXTM3U
#EXTINF:-1 tvg-id="bbcone.uk" tvg-name="BBC One" tvg-logo="https://logos/bbc1.png" group-title="UK",BBC One HD
https://provider/streams/bbc1.m3u8
#EXTINF:-1 tvg-id="zdf.de" tvg-name="ZDF" group-title="DE",ZDF
https://provider/streams/zdf.m3u8
`;

    const r = parseM3U(input);

    expect(r.channels).toHaveLength(2);
    expect(r.channels[0]).toMatchObject({
      tvgId: 'bbcone.uk',
      tvgName: 'BBC One',
      logo: 'https://logos/bbc1.png',
      group: 'UK',
      name: 'BBC One HD',
      durationSeconds: -1,
      url: 'https://provider/streams/bbc1.m3u8',
    });
    expect(r.channels[1]).toMatchObject({
      tvgId: 'zdf.de',
      group: 'DE',
      name: 'ZDF',
      url: 'https://provider/streams/zdf.m3u8',
    });
    expect(r.malformedEntries).toBe(0);
  });

  it('tolerates a missing #EXTM3U header', () => {
    const input = `#EXTINF:-1 tvg-id="x",X\nhttps://x/x`;
    const r = parseM3U(input);
    expect(r.channels).toHaveLength(1);
  });

  it('counts malformed #EXTINF entries instead of crashing', () => {
    const input = `#EXTM3U
#EXTINF:notanumber,Bad
https://still-a-url
#EXTINF:-1 tvg-id="ok",Good
https://ok
`;
    const r = parseM3U(input);
    expect(r.malformedEntries).toBe(1);
    expect(r.channels).toHaveLength(1);
    expect(r.channels[0].name).toBe('Good');
  });

  it('skips comment lines that are not #EXTINF and counts them as ignored', () => {
    const input = `#EXTM3U
# this is a custom comment
#EXTVLCOPT:network-caching=300
#EXTINF:-1,Only
https://only
`;
    const r = parseM3U(input);
    expect(r.channels).toHaveLength(1);
    expect(r.ignoredLines).toBe(2);
  });
});

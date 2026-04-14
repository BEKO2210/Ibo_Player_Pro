import { Injectable } from '@nestjs/common';

export type FetchFn = (url: string, init?: { headers?: Record<string, string> }) => Promise<{
  ok: boolean;
  status: number;
  text: () => Promise<string>;
}>;

/**
 * Thin HTTP fetch wrapper — kept as its own injectable so tests can
 * override the whole transport layer without reaching into OkHttp-style
 * interceptors. Default implementation uses Node 22's built-in `fetch`.
 */
@Injectable()
export class EpgFetcher {
  private transport: FetchFn = (url, init) => fetch(url, init);

  /** Injected by tests to point at a mock HTTP server. */
  setTransport(fn: FetchFn): void {
    this.transport = fn;
  }

  async fetchText(url: string, headers?: Record<string, string>): Promise<string> {
    const response = await this.transport(url, { headers });
    if (!response.ok) {
      throw new Error(`XMLTV fetch failed: HTTP ${response.status} from ${url}`);
    }
    return response.text();
  }
}

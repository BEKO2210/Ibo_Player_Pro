/**
 * Premium TV Player — API contracts (Zod).
 *
 * Re-exports the three part modules plus shared primitives. Both the NestJS
 * backend (services/api, Run 6+) and the Android TV client (apps/android-tv,
 * Run 11+) consume this package.
 */
export * from './common';
export * from './part-1-identity';
export * from './part-2-commerce-sources';
export * from './part-3-epg-activity';

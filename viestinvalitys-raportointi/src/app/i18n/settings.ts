import type { InitOptions } from 'i18next';

export const FALLBACK_LOCALE = 'fi';
export const supportedLocales = ['en', 'fi'] as const;
export type Locales = (typeof supportedLocales)[number];

// You can name the cookie to whatever you want
export const LANGUAGE_COOKIE = 'preferred_language';

export function getOptions(lang = FALLBACK_LOCALE): InitOptions {
  return {
    // debug: true, // Set to true to see console logs
    supportedLngs: supportedLocales,
    fallbackLng: FALLBACK_LOCALE,
    lng: lang,
  };
}

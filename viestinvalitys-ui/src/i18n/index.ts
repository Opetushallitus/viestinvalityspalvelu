import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import fi from './messages/fi.json';
import sv from './messages/sv.json';
import en from './messages/en.json';

const LOCALISATIONS_URL = '/viestinvalityspalvelu/v1/localisations';
const LOCALES = ['fi', 'sv', 'en'] as const;
type Locale = (typeof LOCALES)[number];

type LocalisationDto = { key: string; locale: string; value: string };

function readLangCookie(): Locale {
  const match = document.cookie.split('; ').find((c) => c.startsWith('lang='));
  const value = match?.split('=')[1];
  return value && (LOCALES as readonly string[]).includes(value) ? (value as Locale) : 'fi';
}

async function fetchLokalisaatiot(): Promise<Record<Locale, Record<string, string>>> {
  const grouped: Record<Locale, Record<string, string>> = { fi: {}, sv: {}, en: {} };
  try {
    const res = await fetch(LOCALISATIONS_URL);
    if (!res.ok) return grouped;
    const items: LocalisationDto[] = await res.json();
    for (const item of items) {
      if (item.locale in grouped) {
        grouped[item.locale as Locale][item.key] = item.value;
      }
    }
  } catch {
    // ignore — the bundled default messages are used as fallback
  }
  return grouped;
}

function flattenTranslations(obj: Record<string, unknown>, prefix = ''): Record<string, string> {
  return Object.entries(obj).reduce(
    (acc, [key, value]) => {
      const fullKey = prefix ? `${prefix}.${key}` : key;
      if (typeof value === 'object' && value !== null) {
        Object.assign(acc, flattenTranslations(value as Record<string, unknown>, fullKey));
      } else {
        acc[fullKey] = String(value);
      }
      return acc;
    },
    {} as Record<string, string>,
  );
}

const flatFi = flattenTranslations(fi);
const flatSv = flattenTranslations(sv);
const flatEn = flattenTranslations(en);

i18n.use(initReactI18next).init({
  resources: {
    fi: { translation: flatFi },
    sv: { translation: flatSv },
    en: { translation: flatEn },
  },
  lng: readLangCookie(),
  fallbackLng: 'fi',
  interpolation: {
    prefix: '{',
    suffix: '}',
    escapeValue: false,
  },
});

// Fetch remote translations asynchronously and merge over the bundled defaults
fetchLokalisaatiot().then((byLocale) => {
  (Object.keys(byLocale) as Locale[]).forEach((lang) => {
    const remote = byLocale[lang];
    if (Object.keys(remote).length > 0) {
      i18n.addResourceBundle(lang, 'translation', remote, true, true);
    }
  });
});

export default i18n;

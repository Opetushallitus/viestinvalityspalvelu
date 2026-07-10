import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import fi from './messages/fi.json';
import sv from './messages/sv.json';
import en from './messages/en.json';

const VIRKAILIJA_URL = '';

async function fetchLokalisaatiot(lang: string): Promise<Record<string, string>> {
  try {
    const url = `${VIRKAILIJA_URL}/lokalisointi/cxf/rest/v1/localisation?category=viestinvalitys&locale=${lang}`;
    const res = await fetch(url);
    if (!res.ok) return {};
    const items: { key: string; value: string }[] = await res.json();
    return Object.fromEntries(items.map((item) => [item.key, item.value]));
  } catch {
    return {};
  }
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
  lng: 'fi',
  fallbackLng: 'fi',
  interpolation: {
    escapeValue: false,
  },
});

// Fetch remote translations asynchronously and merge
(['fi', 'sv', 'en'] as const).forEach(async (lang) => {
  const remote = await fetchLokalisaatiot(lang);
  if (Object.keys(remote).length > 0) {
    i18n.addResourceBundle(lang, 'translation', remote, true, true);
  }
});

export default i18n;

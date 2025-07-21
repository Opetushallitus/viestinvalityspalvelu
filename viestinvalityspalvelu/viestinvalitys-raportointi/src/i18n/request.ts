import { getRequestConfig } from 'next-intl/server';
import { set } from 'lodash';
import { isLocal } from '../app/lib/configurations';
import { fetchAsiointikieli, fetchLokalisaatiot } from '../app/lib/data';

const FALLBACK_LOCALE = 'fi';

async function getLocale() {
  if (isLocal) {
    return FALLBACK_LOCALE; // lokaalisti kovakoodattu asiointikieli koska ONR ei salli kutsuja
  }
  const data = await fetchAsiointikieli();
  return data ?? 'fi';
}

// next-intl ei salli pisteitä avaimissa
// ks. https://github.com/amannn/next-intl/discussions/148#discussioncomment-4274218
export function removeDotsFromTranslations(
  translations: { [s: string]: string } | ArrayLike<string>,
) {
  return Object.entries(translations).reduce(
    (acc, [key, value]) => set(acc, key, value),
    {},
  );
}

export async function getKaannokset(lng: string) {
  const lokalisoinnit = await fetchLokalisaatiot(lng);
  const translations: Record<string, string> = {};
  for (const translation of lokalisoinnit) {
    translations[translation.key] = translation.value;
  }
  return removeDotsFromTranslations(translations);
}

export default getRequestConfig(async () => {
  const locale = await getLocale();
  const messages = await getKaannokset(locale);

  return {
    locale,
    messages,
  };
});

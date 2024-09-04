/* eslint-disable @typescript-eslint/no-unused-vars */
import i18n from 'i18next';
import { createInstance } from 'i18next';
import ChainedBackend, { ChainedBackendOptions } from 'i18next-chained-backend';
import FetchBackend from 'i18next-fetch-backend';
import resourcesToBackend from 'i18next-resources-to-backend';
import { initReactI18next } from 'react-i18next/initReactI18next';
import { fetchAsiointikieli } from '../lib/data';
import { LanguageCode } from '../lib/types';
import { supportedLocales } from '../lib/constants';
import { raportointiUrl } from '../lib/configurations';

export const FALLBACK_LOCALE = 'fi';

async function initI18nextForServer(lang: LanguageCode) {
  const i18nInstance = createInstance();
  // odotetaan että resurssit on ladattu
  await i18nInstance
    .use(FetchBackend)
    .use(initReactI18next)
    .init({
      // debug: true,
      supportedLngs: supportedLocales,
      fallbackLng: FALLBACK_LOCALE,
      preload: supportedLocales,
      lng: lang,
      backend: {
        loadPath: `${raportointiUrl}/api/lokalisointi/?lng={{lng}}`
      },
    });
  return i18nInstance;
}

// käännökset serverikomponenteille
export async function initTranslations() {
  const lang = await getLocale();
  const i18nextInstance = await initI18nextForServer(lang);
  return {
    t: i18nextInstance.getFixedT(lang, 'translation') // default namespace
  };
}

export async function getLocale() {
  const data = await fetchAsiointikieli();
  return data?.asiointikieli ?? 'fi';
}


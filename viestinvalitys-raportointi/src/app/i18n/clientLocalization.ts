'use client';
import ChainedBackend from 'i18next-chained-backend';
import FetchBackend from 'i18next-fetch-backend';
import resourcesToBackend from 'i18next-resources-to-backend';
import {
  initReactI18next,
  useTranslation as useTranslationOrig,
} from 'react-i18next';
import { supportedLocales } from '../lib/constants';
import i18next, { i18n } from 'i18next';
import { useLocale } from './locale-provider';
import { LanguageCode } from '../lib/types';
import { useEffect } from 'react';

// mukailtu tätä https://carlogino.com/blog/nextjs-app-dir-i18n-cookie

const runsOnServerSide = typeof window === 'undefined';
console.log(runsOnServerSide);
// Initialize i18next for the client side
i18next
  .use(ChainedBackend)
  .use(initReactI18next)
  .init({
    //debug: true,
    supportedLngs: supportedLocales,
    fallbackLng: false,
    lng: 'fi', // default alustukseen
    backend: {
      backends: [
        FetchBackend,
        resourcesToBackend((lng: string) => import(`./locales/${lng}.json`)), // lokaalidevaukseen backup tiedostoista
      ],
      loadPath: '/api/lokalisointi/?lng={{lng}}',
    },
    preload: supportedLocales,
  });

// custom hook koska pitää hanskata mahd kielen vaihtuminen  
export function useTranslation() {
  const lng = useLocale();
  const translator = useTranslationOrig();
  const { i18n } = translator;
  // hanskataan mahdollinen kielen vaihto client&server
  if (runsOnServerSide && lng && i18n.resolvedLanguage !== lng) {
    i18n.changeLanguage(lng);
  } else {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useChangeLanguage(i18n, lng);
  }
  return translator;
}

function useChangeLanguage(i18n: i18n, lng: LanguageCode) {
  // Kielen vaihto client-komponenteille
  useEffect(() => {
    if (!lng || i18n.resolvedLanguage === lng) return;
    i18n.changeLanguage(lng);
  }, [lng, i18n]);
}


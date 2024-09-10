
import { createInstance } from "i18next";
import resourcesToBackend from "i18next-resources-to-backend";

export async function initI18nextForTests() {
    const i18nInstance = createInstance();
    // varmistetaan että resurssi ladattu jotta testit ei turhaan failaa
    await i18nInstance
    // suomikäännös riittää
    .use(resourcesToBackend(() => import(`./locales/fi.json`)))
    .init()
    return i18nInstance;
  }
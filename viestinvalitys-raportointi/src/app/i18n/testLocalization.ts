import i18next from "i18next";
import resourcesToBackend from "i18next-resources-to-backend";

i18next
  .use(resourcesToBackend(() => import(`./locales/fi.json`)))
  .init({ 
    debug: true/* other options */ })

export default i18next;
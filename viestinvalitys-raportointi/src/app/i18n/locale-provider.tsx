'use client';

import { createContext, useContext } from "react";
import { LanguageCode } from "../lib/types";
import { FALLBACK_LOCALE } from "./localization";

const Context = createContext<LanguageCode>(FALLBACK_LOCALE);

// I18nextProvider kanssa meni hankalaksi, joten tässä karvalakkimalli localen välitykseen clientille
export function LocaleProvider({
  children,
  value,
}: {
  children: React.ReactNode;
  value: LanguageCode;
}) {
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useLocale() {
  return useContext(Context);
}
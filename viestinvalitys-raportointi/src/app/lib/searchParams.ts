import { createSearchParamsCache, parseAsString } from "nuqs/server";

export const searchParamsCache = createSearchParamsCache({
    seuraavatAlkaen: parseAsString,
    hakukentta: parseAsString,
    hakusana: parseAsString,
    palvelu: parseAsString,
    organisaatio: parseAsString,
    alkaen: parseAsString,
    tila: parseAsString
  })
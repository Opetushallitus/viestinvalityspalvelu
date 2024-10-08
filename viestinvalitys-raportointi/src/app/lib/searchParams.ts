import { createSearchParamsCache, parseAsString } from "nuqs/server";

export const searchParamsCache = createSearchParamsCache({
    seuraavatAlkaen: parseAsString,
    hakukentta: parseAsString,
    hakusana: parseAsString,
    organisaatio: parseAsString,
    alkaen: parseAsString,
    sivutustila: parseAsString,
    tila: parseAsString
  })
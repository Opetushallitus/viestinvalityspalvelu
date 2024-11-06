import { createSearchParamsCache, parseAsString } from "nuqs/server";

export const searchParamsCache = createSearchParamsCache({
    seuraavatAlkaen: parseAsString,
    hakusana: parseAsString,
    palvelu: parseAsString,
    organisaatio: parseAsString,
    alkaen: parseAsString,
    tila: parseAsString,
    hakuAlkaen: parseAsString,
    hakuPaattyen: parseAsString
  })
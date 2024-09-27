import dayjs, { Dayjs } from "dayjs";
import { createParser, createSearchParamsCache, parseAsString } from "nuqs/server";

export const parseAsDayjs = createParser({
  parse: queryValue => {
    const d = dayjs(queryValue)
    if(d.isValid()) 
      return d
    return null
  },
  serialize: (value: Dayjs | null) => value?.toString() ?? ''
})

export const searchParamsCache = createSearchParamsCache({
    seuraavatAlkaen: parseAsString,
    hakukentta: parseAsString,
    hakusana: parseAsString,
    palvelu: parseAsString,
    organisaatio: parseAsString,
    alkaen: parseAsString,
    tila: parseAsString,
    hakuAlkaen: parseAsDayjs,
    hakuPaattyen: parseAsDayjs
  })
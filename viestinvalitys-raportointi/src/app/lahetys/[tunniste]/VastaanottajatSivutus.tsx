'use client'
import { usePathname, useSearchParams } from "next/navigation"
import { useCallback } from "react"
import { Link as MuiLink } from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import NextLink from 'next/link';

const VastaanottajatSivutus = ({ seuraavatAlkaen, viimeisenTila }: { seuraavatAlkaen?: string, viimeisenTila: string }) => {
    type UrlParam = {
        name: string, value: string
    }
    const pathname = usePathname()
    const searchParams = useSearchParams()
    // Get a new searchParams string by merging the current
    // searchParams with a provided key/value pair
    const createQueryString = useCallback(
      (newparams: UrlParam[]) => {
        const params = new URLSearchParams(searchParams?.toString() || '')
        newparams.map(p => params.set(p.name, p.value))
   
        return params.toString()
      },
      [searchParams]
    )
   
    return (
        seuraavatAlkaen ?        
          <MuiLink component={NextLink} href={pathname+'/?' + createQueryString([{name:'alkaen', value: seuraavatAlkaen},{name:'sivutustila', value: viimeisenTila}])}>
          Seuraavat
          </MuiLink>
        : <></>
    )
  }

  export default VastaanottajatSivutus
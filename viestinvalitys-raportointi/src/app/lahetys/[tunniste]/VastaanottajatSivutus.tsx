'use client'
import { usePathname } from "next/navigation"
import { Link as MuiLink } from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import NextLink from 'next/link';
import useQueryParams from "@/app/hooks/useQueryParams";

const VastaanottajatSivutus = ({ seuraavatAlkaen, viimeisenTila }: { seuraavatAlkaen?: string, viimeisenTila: string }) => {
  const { createQueryStrings } = useQueryParams();
    const pathname = usePathname()
   
    return (
        seuraavatAlkaen ?        
          <MuiLink component={NextLink} prefetch={false} href={pathname+'/?' + createQueryStrings([{name:'alkaen', value: seuraavatAlkaen},{name:'sivutustila', value: viimeisenTila}])}>
          Seuraavat
          </MuiLink>
        : <></>
    )
  }

  export default VastaanottajatSivutus
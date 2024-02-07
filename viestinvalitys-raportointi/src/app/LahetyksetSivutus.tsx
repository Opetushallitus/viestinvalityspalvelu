'use client'
import { useSearchParams } from "next/navigation"
import { useCallback } from "react"
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import NextLink from 'next/link';

const LahetyksetSivutus = ({ seuraavatAlkaen }: { seuraavatAlkaen?: string }) => {
    const searchParams = useSearchParams()
    // Get a new searchParams string by merging the current
    // searchParams with a provided key/value pair
    const createQueryString = useCallback(
      (name: string, value: string) => {
        const params = new URLSearchParams(searchParams?.toString() || '')
        params.set(name, value)
   
        return params.toString()
      },
      [searchParams]
    )

    return (
        seuraavatAlkaen ?
          <Button aria-label="seuraavat" href={'/?' + createQueryString('seuraavatAlkaen', seuraavatAlkaen)} 
          size="large" component={NextLink} endIcon={<ChevronRightIcon />}>
            Seuraavat
          </Button>
        : <></>
    )
  }

  export default LahetyksetSivutus
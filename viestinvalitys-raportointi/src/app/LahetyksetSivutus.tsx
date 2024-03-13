'use client'
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import NextLink from 'next/link';
import useQueryParams from "./hooks/useQueryParams";

const LahetyksetSivutus = ({ seuraavatAlkaen }: { seuraavatAlkaen?: string }) => {
    const { createQueryString } = useQueryParams();

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
'use client'
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
import Link from 'next/link';
import useQueryParams from "./hooks/useQueryParams";

const LahetyksetSivutus = ({ seuraavatAlkaen }: { seuraavatAlkaen?: string }) => {
    const { createQueryString } = useQueryParams();

    return (
      seuraavatAlkaen ?
      <Link href={'/?' + createQueryString('seuraavatAlkaen', seuraavatAlkaen)} prefetch={false}> 
        <Button aria-label="seuraavat" size="large" endIcon={<ChevronRightIcon />}>
          Seuraavat
        </Button>
      </Link>
      : <></>
  )
  }

  export default LahetyksetSivutus
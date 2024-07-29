'use client';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
import Link from 'next/link';
import useQueryParams from './hooks/useQueryParams';

const LahetyksetSivutus = ({
  seuraavatAlkaen,
}: {
  seuraavatAlkaen?: string;
}) => {
  const { createQueryString } = useQueryParams();
  return seuraavatAlkaen ? (

    <Button
      component={Link}
      href={'/?' + createQueryString('seuraavatAlkaen', seuraavatAlkaen)}
      aria-label="seuraavat"
      size="large"
      endIcon={<ChevronRightIcon />}
      prefetch={false}
    >
      Seuraavat
    </Button>
  ) : (
    <></>
  );
};

export default LahetyksetSivutus;

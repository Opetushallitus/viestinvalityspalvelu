'use client';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
import useQueryParams from '@/app/hooks/useQueryParams';
import { useTranslations } from 'next-intl';

const VastaanottajatSivutus = ({
  seuraavatAlkaen,
}: {
  seuraavatAlkaen?: string;
}) => {
  const pathname = usePathname();
  const { createQueryStrings } = useQueryParams();
  const t = useTranslations();

  return seuraavatAlkaen ? (
    <Button
      component={Link}
      href={
        pathname +
        '/?' +
        createQueryStrings([
          { name: 'alkaen', value: seuraavatAlkaen },
        ])
      }
      aria-label={t('yleinen.sivutus.seuraavat')}
      size="large"
      endIcon={<ChevronRightIcon />}
      prefetch={false}
    >
      {t('yleinen.sivutus.seuraavat')}
    </Button>
  ) : (
    <></>
  );
};

export default VastaanottajatSivutus;

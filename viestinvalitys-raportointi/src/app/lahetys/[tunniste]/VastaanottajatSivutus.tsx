'use client';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
import useQueryParams from '@/app/hooks/useQueryParams';
import { useTranslation } from '@/app/i18n/clientLocalization';

const VastaanottajatSivutus = ({
  seuraavatAlkaen,
  viimeisenTila,
}: {
  seuraavatAlkaen?: string;
  viimeisenTila: string;
}) => {
  const pathname = usePathname();
  const { createQueryStrings } = useQueryParams();
  const { t } = useTranslation();

  return seuraavatAlkaen ? (
    <Button
      component={Link}
      href={
        pathname +
        '/?' +
        createQueryStrings([
          { name: 'alkaen', value: seuraavatAlkaen },
          { name: 'sivutustila', value: viimeisenTila },
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

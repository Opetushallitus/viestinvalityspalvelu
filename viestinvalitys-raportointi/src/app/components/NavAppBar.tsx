'use client';
import HomeIconLink from './HomeIconLink';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import OrganisaatioSelect from './OrganisaatioSelect';
import { Suspense } from 'react';
import Loading from './Loading';
import { colors } from '../theme';
import { PageContent } from './PageContent';
import { Typography } from '@opetushallitus/oph-design-system';
import { usePathname } from 'next/navigation';
import { useTranslation } from '../i18n/clientLocalization';

export default function Header() {
  const currentRoute = usePathname();
  const isHome = currentRoute == '/';
  const { t } = useTranslation();
  return (
    <header
      style={{
        position: 'relative',
        backgroundColor: colors.white,
        width: '100%',
        border: `2px solid ${colors.grey100}`,
      }}
    >
      <PageContent
        sx={{
          paddingY: 2,
          display: 'flex',
          alignItems: 'center',
          columnGap: 2,
        }}
      >
        {isHome ? (
          <Typography variant="h1">{t('lahetykset.otsikko')}</Typography>
        ) : (
          <>
            <HomeIconLink />
            <Typography variant="h2">
              <NavigateNextIcon sx={{ color: colors.grey500 }} />
              {t('navigointi.lahetykset')}
            </Typography>
          </>
        )}
        <Suspense fallback={<Loading />}>
          <OrganisaatioSelect />
        </Suspense>
      </PageContent>
    </header>
  );
}


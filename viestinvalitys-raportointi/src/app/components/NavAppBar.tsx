'use client';
import HomeIconLink from './HomeIconLink';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import OrganisaatioSelect from './OrganisaatioSelect';
import { Suspense } from 'react';
import Loading from './Loading';
import { PageContent } from './PageContent';
import { ophColors, OphTypography } from '@opetushallitus/oph-design-system';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';

export default function Header() {
  const currentRoute = usePathname();
  const isHome = currentRoute == '/';
  const t = useTranslations();
  return (
    <header
      style={{
        position: 'relative',
        backgroundColor: ophColors.white,
        width: '100%',
        border: `2px solid ${ophColors.grey100}`,
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
          <OphTypography variant="h1">{t('lahetykset.otsikko')}</OphTypography>
        ) : (
          <>
            <HomeIconLink />
            <OphTypography variant="h2">
              <NavigateNextIcon sx={{ color: ophColors.grey500 }} />
              {t('navigointi.lahetykset')}
            </OphTypography>
          </>
        )}
        <Suspense fallback={<Loading />}>
          <OrganisaatioSelect />
        </Suspense>
      </PageContent>
    </header>
  );
}


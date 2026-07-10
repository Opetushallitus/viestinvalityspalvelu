import { Suspense } from 'react';
import HomeIconLink from './HomeIconLink';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import OrganisaatioSelect from './OrganisaatioSelect';
import Loading from './Loading';
import { PageContent } from './PageContent';
import { ophColors, OphTypography } from '@opetushallitus/oph-design-system';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export default function Header() {
  const { pathname } = useLocation();
  const isHome = pathname === '/';
  const { t } = useTranslation();
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

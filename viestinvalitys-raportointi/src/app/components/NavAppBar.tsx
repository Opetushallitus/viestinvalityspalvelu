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

export default function Header() {
  const currentRoute = usePathname();
  const isHome = currentRoute == '/';

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
          <Typography variant="h1">Viestien raportit</Typography>
        ) : (
          <>
            <HomeIconLink />
            <Typography variant="h2">
              <NavigateNextIcon sx={{ color: colors.grey500 }} />
              Lähetysraportti
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


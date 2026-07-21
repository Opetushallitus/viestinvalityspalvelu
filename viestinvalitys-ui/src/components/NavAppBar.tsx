import { Suspense } from 'react';
import OrganisaatioSelect from './OrganisaatioSelect';
import Loading from './Loading';
import { PageContent } from './PageContent';
import { TopNavigation } from './navigation/TopNavigation';
import { ophColors } from '@opetushallitus/oph-design-system';

export default function Header() {
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
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          columnGap: 2,
        }}
      >
        <TopNavigation />
        <Suspense fallback={<Loading />}>
          <OrganisaatioSelect />
        </Suspense>
      </PageContent>
    </header>
  );
}

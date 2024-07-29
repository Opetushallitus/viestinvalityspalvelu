'use client';
import UiVirkailijaRaamit from '@opetushallitus/virkailija-ui-components/VirkailijaRaamit';

const VirkailijaRaamit = ({ virkailijaUrl }: { virkailijaUrl: string }) => {
    const raamitUrl = `${virkailijaUrl}/virkailija-raamit/apply-raamit.js`;

  return <UiVirkailijaRaamit scriptUrl={raamitUrl} />;
};

export default VirkailijaRaamit;
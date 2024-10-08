'use client';
import { HomeOutlined } from '@mui/icons-material';
import { OphButton } from '@opetushallitus/oph-design-system';
import { useTranslations } from 'next-intl';

const HomeIconLink = () => {
  const t = useTranslations();
  return (
    <OphButton
      startIcon={<HomeOutlined />}
      aria-label={t('yleinen.palaa-etusivulle')}
      href="/"
      sx={{ border: '1px solid', borderRadius: '5px', width: 30, height: 30 }}
    >
    </OphButton>
  );
};

export default HomeIconLink;

'use client';
import { HomeOutlined } from '@mui/icons-material';
import { OphButton } from '@opetushallitus/oph-design-system';
import { useTranslation } from '../i18n/clientLocalization';

const HomeIconLink = () => {
  const { t } = useTranslation();
  return (
    <OphButton
      startIcon={<HomeOutlined />}
      aria-label={t('yleinen.palaa.etusivulle')}
      href="/"
      sx={{ border: '1px solid', borderRadius: '5px', width: 30, height: 30 }}
    >
    </OphButton>
  );
};

export default HomeIconLink;

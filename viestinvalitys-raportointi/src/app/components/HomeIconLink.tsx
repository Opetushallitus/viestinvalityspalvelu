'use client';
import { HomeOutlined } from '@mui/icons-material';
import { Button } from '@opetushallitus/oph-design-system';
import { useTranslation } from '../i18n/clientLocalization';

const HomeIconLink = () => {
  const { t } = useTranslation();
  return (
    <Button
      startIcon={<HomeOutlined />}
      aria-label={t('yleinen.palaa.etusivulle')}
      href="/"
      sx={{ border: '1px solid', borderRadius: '5px', width: 30, height: 30 }}
    >
    </Button>
  );
};

export default HomeIconLink;

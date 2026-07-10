import { HomeOutlined } from '@mui/icons-material';
import { OphButton } from '@opetushallitus/oph-design-system';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

const HomeIconLink = () => {
  const { t } = useTranslation();
  return (
    <OphButton
      component={Link}
      to="/"
      startIcon={<HomeOutlined />}
      aria-label={t('yleinen.palaa-etusivulle')}
      sx={{ border: '1px solid', borderRadius: '5px', width: 30, height: 30 }}
    ></OphButton>
  );
};

export default HomeIconLink;

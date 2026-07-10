import { Box, CircularProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';

const Loading = () => {
  const { t } = useTranslation();
  return (
    <Box sx={{ display: 'flex' }}>
      <CircularProgress aria-label={t('yleinen.ladataan')} />
    </Box>
  );
};

export default Loading;

'use client';
import { Box, CircularProgress } from '@mui/material';
import { useTranslation } from '../i18n/clientLocalization';

const Loading = () => {
  const { t } = useTranslation();
  return (
    <Box sx={{ display: 'flex' }}>
      <CircularProgress aria-label={t('yleinen.ladataan')}/>
    </Box>
  );
};

export default Loading;

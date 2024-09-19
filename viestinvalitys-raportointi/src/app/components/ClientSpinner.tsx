'use client';

import { CircularProgress, CircularProgressProps } from '@mui/material';
import { useTranslation } from '../i18n/clientLocalization';

export const ClientSpinner = (props: CircularProgressProps) => {
  const { t } = useTranslation();
  return <CircularProgress aria-label={t('yleinen.ladataan')} {...props} />;
};

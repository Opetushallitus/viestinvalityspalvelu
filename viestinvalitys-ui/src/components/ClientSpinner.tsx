import { CircularProgress, CircularProgressProps } from '@mui/material';
import { useTranslation } from 'react-i18next';

export const ClientSpinner = (props: CircularProgressProps) => {
  const { t } = useTranslation();
  return <CircularProgress aria-label={t('yleinen.ladataan')} {...props} />;
};

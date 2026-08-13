import { useState } from 'react';
import { Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
import { OphButton, OphTypography } from '@opetushallitus/oph-design-system';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchViesti } from '../lib/api';
import { Viesti } from '../lib/types';
import { SanitizedHtml } from './SanitizedHtmlComponent';

const ViestiModal = ({
  viestiTunniste,
  onClose,
}: {
  viestiTunniste: string;
  onClose: () => void;
}) => {
  const { t } = useTranslation();
  const { data, isLoading } = useQuery<Viesti>({
    queryKey: ['fetchViesti', viestiTunniste],
    queryFn: () => fetchViesti(viestiTunniste),
  });
  if (isLoading) {
    return <OphTypography>{t('yleinen.ladataan')}</OphTypography>;
  }
  return (
    <Dialog open onClose={onClose} fullWidth aria-labelledby="viesti-dialog-title">
      <DialogTitle id="viesti-dialog-title">{data?.otsikko ?? t('viesti.ei-otsikkoa')}</DialogTitle>
      <DialogContent>
        {data?.sisallonTyyppi === 'HTML' ? (
          <SanitizedHtml html={data?.sisalto ?? ''} />
        ) : (
          <OphTypography component="div">{data?.sisalto ?? t('viesti.ei-sisaltoa')}</OphTypography>
        )}
      </DialogContent>
      <DialogActions>
        <OphButton variant="contained" onClick={onClose}>
          {t('yleinen.sulje')}
        </OphButton>
      </DialogActions>
    </Dialog>
  );
};

const ViewViesti = ({ viestiTunniste }: { viestiTunniste: string }) => {
  const { t } = useTranslation();
  const [viestiOpen, setViestiOpen] = useState(false);
  return (
    <>
      <OphButton onClick={() => setViestiOpen(true)}>{t('viesti.nayta')}</OphButton>
      {viestiOpen ? (
        <ViestiModal viestiTunniste={viestiTunniste} onClose={() => setViestiOpen(false)} />
      ) : (
        <></>
      )}
    </>
  );
};

export default ViewViesti;

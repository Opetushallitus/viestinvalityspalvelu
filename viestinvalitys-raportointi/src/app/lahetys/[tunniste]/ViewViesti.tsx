'use client';
import DOMPurify from 'dompurify';
import { useState } from 'react';
import { DialogContentText } from '@mui/material';
import { fetchViesti } from '@/app/lib/data';
import { Viesti } from '@/app/lib/types';
import { useQuery } from '@tanstack/react-query';
import { OphButton, OphTypography } from '@opetushallitus/oph-design-system';
import { useTranslations } from 'next-intl';
import { OphModalDialog } from '@/app/components/oph-modal-dialog';

const ViestiModal = ({
  viestiTunniste,
  open,
  handleClose,
}: {
  viestiTunniste: string;
  open: boolean;
  handleClose: () => void;
}) => {
  const doFetchViesti = async (viestiTunniste: string): Promise<Viesti> => {
    const response = await fetchViesti(viestiTunniste);
    return response;
  };

  const { data, isLoading } = useQuery({
    queryKey: ['fetchViesti', viestiTunniste],
    queryFn: () => doFetchViesti(viestiTunniste),
  });
  const t = useTranslations();
  if (isLoading) {
    return <OphTypography>{t('yleinen.ladataan')}</OphTypography>;
  }
  return (
    <OphModalDialog
      open={open}
      onClose={handleClose}
      aria-labelledby="viesti-dialog-title"
      aria-describedby="viesti-dialog-description"
      title={data?.otsikko ?? t('viesti.ei-otsikkoa')}
      actions={ 
      <OphButton variant='contained' onClick={handleClose}>{t('yleinen.sulje')}</OphButton>
      }
    >
      {/* suppressHydrationWarning jotta viestin sisältämä html ei tuota herjoja */}
        <DialogContentText id="viesti-dialog-description">
          {data?.sisallonTyyppi === 'HTML' ? (
            <div
              suppressHydrationWarning={true}
              dangerouslySetInnerHTML={{
                __html: DOMPurify.sanitize(data?.sisalto),
              }}
            />
          ) : (
            <OphTypography id="modal-viestisisalto" sx={{ mt: 2 }} component="div">
              {data?.sisalto ?? t('viesti.ei-sisaltoa')}
            </OphTypography>
          )}
        </DialogContentText>
    </OphModalDialog>
  );
};

export default function ViewViesti({
  viestiTunniste,
}: {
  viestiTunniste: string;
}) {
  const [viestiOpen, setViestiOpen] = useState(false);
  const handleOpen = () => {
    setViestiOpen(true);
  };
  const handleClose = () => {
    setViestiOpen(false);
  };
  const t = useTranslations();
  return (
    <>
      <OphButton onClick={handleOpen}>{t('viesti.nayta')}</OphButton>
      {viestiOpen ? (
        <ViestiModal
          viestiTunniste={viestiTunniste}
          open={viestiOpen}
          handleClose={handleClose}
        />
      ) : (
        <></>
      )}
    </>
  );
}

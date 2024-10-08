'use client';
/* eslint-disable @typescript-eslint/no-explicit-any */
import DOMPurify from 'dompurify';
import { useState } from 'react';
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
} from '@mui/material';
import { fetchViesti } from '@/app/lib/data';
import { Viesti } from '@/app/lib/types';
import { useQuery } from '@tanstack/react-query';
import { OphButton, ophColors, OphTypography } from '@opetushallitus/oph-design-system';
import CloseIcon from '@mui/icons-material/Close';
import { useTranslations } from 'next-intl';

const closeButtonStyle = {
    position: 'absolute',
    right: "2px",
    top: "2px",
    color: ophColors.grey500,
  };

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
    <Dialog
      open={open}
      onClose={handleClose}
      aria-labelledby="viesti-dialog-title"
      aria-describedby="viesti-dialog-description"
    >
      <DialogTitle id="viesti-dialog-title" component="h3" sx={{borderTop: 4, borderTopColor: ophColors.blue2}}>
        {data?.otsikko ?? t('viesti.ei-otsikkoa')}
        <IconButton aria-label={t('yleinen.sulje')} onClick={handleClose} sx={closeButtonStyle}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
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
            <OphTypography id="modal-viestisisalto" sx={{ mt: 2 }}>
              {data?.sisalto ?? t('viesti.ei-sisaltoa')}
            </OphTypography>
          )}
        </DialogContentText>
      </DialogContent>
      <DialogActions sx={{justifyContent: 'flex-start'}}>
        <OphButton variant='contained' onClick={handleClose}>{t('yleinen.sulje')}</OphButton>
      </DialogActions>
    </Dialog>
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

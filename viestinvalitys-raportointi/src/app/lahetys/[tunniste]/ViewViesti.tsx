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
import { Button, Typography } from '@opetushallitus/oph-design-system';
import CloseIcon from '@mui/icons-material/Close';
import { colors } from '@/app/theme';
import { useTranslation } from '@/app/i18n/clientLocalization';

const closeButtonStyle = {
    position: 'absolute',
    right: "2px",
    top: "2px",
    color: colors.grey500,
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

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['fetchViesti', viestiTunniste],
    queryFn: () => doFetchViesti(viestiTunniste),
  });
  const { t } = useTranslation();
  if (isLoading) {
    return <Typography>{t('yleinen.ladataan')}</Typography>;
  }
  return (
    <Dialog
      open={open}
      onClose={handleClose}
      aria-labelledby="viesti-dialog-title"
      aria-describedby="viesti-dialog-description"
    >
      <DialogTitle id="viesti-dialog-title" component="h3" sx={{borderTop: 4, borderTopColor: colors.blue2}}>
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
            <Typography id="modal-viestisisalto" sx={{ mt: 2 }}>
              {data?.sisalto ?? t('viesti.ei-sisaltoa')}
            </Typography>
          )}
        </DialogContentText>
      </DialogContent>
      <DialogActions sx={{justifyContent: 'flex-start'}}>
        <Button variant='contained' onClick={handleClose}>{t('yleinen.sulje')}</Button>
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
  const { t } = useTranslation();
  return (
    <>
    
      <Button onClick={handleOpen}>{t('viesti.nayta')}</Button>
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

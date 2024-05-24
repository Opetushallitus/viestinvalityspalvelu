'use client';
import DOMPurify from 'dompurify';
import { useState } from 'react';
import useSwr from 'swr';
import { Box, Button, Modal, Typography } from '@mui/material';
import { fetchViesti } from '@/app/lib/data';

const style = {
  position: 'absolute' as 'absolute',
  top: '50%',
  left: '50%',
  transform: 'translate(-50%, -50%)',
  width: 400,
  bgcolor: 'background.paper',
  border: '2px solid #000',
  boxShadow: 24,
  p: 4,
};

const ViestiModal = ({
  viestiTunniste,
  open,
  handleClose,
}: {
  viestiTunniste: string;
  open: any;
  handleClose: any;
}) => {
  const { data, error, isLoading } = useSwr(viestiTunniste, fetchViesti);
  if (isLoading) {
    return <Typography>Ladataan</Typography>;
  }
  console.info(data)
  console.info(error)
  return (
    <Modal
      open={open}
      onClose={handleClose}
      aria-labelledby="modal-viestiotsikko"
      aria-describedby="modal-viestisisalto"
    >
      <Box sx={style}>
        <Typography id="modal-viestiotsikko" variant="h6" component="h2">
          {data?.otsikko || 'ei viestin otsikkoa'}
        </Typography>
        
          {data?.sisallonTyyppi === 'HTML' ? <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(data?.sisalto) }} /> : 
          <Typography id="modal-viestisisalto" sx={{ mt: 2 }}>
          {data?.sisalto || 'ei viestin sisältöä'}
          </Typography>}
      
      </Box>
    </Modal>
  );
};

const ViewViesti = ({ viestiTunniste }: { viestiTunniste: string }) => {
  const [open, setOpen] = useState(false);
  const handleOpen = () => {
    setOpen(true);
  };
  const handleClose = () => {
    setOpen(false);
  };
  console.info('viestitunniste: '+viestiTunniste)
  return (
    <>
      <Button onClick={handleOpen}>Näytä viesti</Button>
      {open ? (
        <ViestiModal
          viestiTunniste={viestiTunniste}
          open={open}
          handleClose={handleClose}
        />
      ) : (
        <></>
      )}
    </>
  );
};

export default ViewViesti;

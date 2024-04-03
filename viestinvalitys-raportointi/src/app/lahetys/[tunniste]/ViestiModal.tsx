'use client';
import { fetchViesti } from '@/app/lib/data';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Modal from '@mui/material/Modal';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import useSwr from 'swr';

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


const ViestiModal = ({ viestiTunniste }: { viestiTunniste: string }) => {
  const { data, error, isLoading } = useSwr(
    viestiTunniste,
    fetchViesti
  );
  const [open, setOpen] = useState(false);
  const handleOpen = () => {
    setOpen(true);
  };
  const handleClose = () => setOpen(false);
  if (isLoading) {
    return <Typography>Ladataan</Typography>;
  }
  return (
    <>
      <Button onClick={handleOpen}>Näytä viesti</Button>
      <Modal
        open={open}
        onClose={handleClose}
        aria-labelledby="modal-viestiotsikko"
        aria-describedby="modal-viestisisalto"
      >
        <Box sx={style}>
          <Typography id="modal-viestiotsikko" variant="h6" component="h2">
            {data.otsikko}
          </Typography>
          <Typography id="modal-viestisisalto" sx={{ mt: 2 }}>
            {data.sisalto}
          </Typography>
        </Box>
      </Modal>
    </>
  );
};

export default ViestiModal;

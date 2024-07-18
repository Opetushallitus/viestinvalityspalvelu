'use client';
/* eslint-disable @typescript-eslint/no-explicit-any */
import DOMPurify from 'dompurify';
import { useState } from 'react';
import { Box, Button, Modal, Typography } from '@mui/material';
import { fetchViesti } from '@/app/lib/data';
import { Viesti } from '@/app/lib/types';
import { useQuery } from '@tanstack/react-query';

const style = {
  position: 'absolute',
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
  const doFetchViesti = async (viestiTunniste: string): Promise<Viesti> => {
    // TODO tsekkaa että parametri löytyy
    const response = await fetchViesti(viestiTunniste)
    return response.data
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['fetchViesti', viestiTunniste],
    queryFn: () => doFetchViesti(viestiTunniste),
  })

  if (isLoading) {
    return <Typography>Ladataan</Typography>;
  }
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

        {data?.sisallonTyyppi === 'HTML' ? (
          <div
            dangerouslySetInnerHTML={{
              __html: DOMPurify.sanitize(data?.sisalto),
            }}
          />
        ) : (
          <Typography id="modal-viestisisalto" sx={{ mt: 2 }}>
            {data?.sisalto || 'ei viestin sisältöä'}
          </Typography>
        )}
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

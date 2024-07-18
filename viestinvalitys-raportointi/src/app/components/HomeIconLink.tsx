'use client';
import { IconButton } from '@mui/material';
import { HomeOutlined } from '@mui/icons-material';
import Link from 'next/link';

const HomeIconLink = () => {
  return (
    <IconButton
      aria-label="palaa etusivulle"
      href="/"
      size="large"
      component={Link}
      prefetch={false}
      sx={{ border: '1px solid', borderRadius: '5px', width: 30, height: 30 }}
    >
      <HomeOutlined />
    </IconButton>
  );
};

export default HomeIconLink;

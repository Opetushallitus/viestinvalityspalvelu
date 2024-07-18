'use client';
import { AppBar, Box, Toolbar } from '@mui/material';
import HomeIconLink from './HomeIconLink';
import OrganisaatioSelect from './OrganisaatioSelect';
import { Suspense } from 'react';
import Loading from './Loading';

const NavAppBar = () => {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static" sx={{ backgroundColor: 'white' }}>
        <Toolbar>
          <HomeIconLink />
          <Suspense fallback={<Loading />}>
            <OrganisaatioSelect />
          </Suspense>
        </Toolbar>
      </AppBar>
    </Box>
  );
};

export default NavAppBar;

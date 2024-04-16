'use client'
import { AppBar, Box, Drawer, IconButton, Toolbar, Typography } from '@mui/material';
import HomeIconLink from './HomeIconLink';
import MenuIcon from '@mui/icons-material/Menu';
import { Organisaatio } from '../lib/types';
import OrganisaatioSelect from './OrganisaatioSelect';
import { useState } from 'react';
import useQueryParams from '../hooks/useQueryParams';
import { findOrganisaatioByOid, parseExpandedParents } from '../lib/util';
import { useSearchParams } from 'next/navigation';
import OrganisaatioFilter from './OrganisaatioFilter';

const NavAppBar = () => {
    const [open, setOpen] = useState(false);
    const [selectedOrg] = useState<Organisaatio>();
    const toggleDrawer = (newOpen: boolean) => () => {
      setOpen(newOpen);
    };

  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static"   
      sx={{backgroundColor: 'white'}}>
        <Toolbar>
          <HomeIconLink />
          <Typography component="div" sx={{ ml: 2, flexGrow: 1, color: 'black'}}>
            {selectedOrg?.nimi?.fi}
          </Typography>
          <IconButton
            onClick={toggleDrawer(true)}
            title="vaihda organisaatiota">
            <MenuIcon />
            </IconButton>
            <Drawer open={open} onClose={toggleDrawer(false)} anchor='right'>
              <OrganisaatioFilter />
            </Drawer>
        </Toolbar>
      </AppBar>
    </Box>
  );
};

export default NavAppBar;

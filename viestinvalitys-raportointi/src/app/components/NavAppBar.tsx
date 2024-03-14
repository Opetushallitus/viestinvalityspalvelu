'use client'
import { AppBar, Box, Drawer, IconButton, Toolbar, Typography } from '@mui/material';
import HomeIconLink from './HomeIconLink';
import MenuIcon from '@mui/icons-material/Menu';
import { Organisaatio } from '../lib/types';
import OrganisaatioSelect from './OrganisaatioSelect';
import { useState } from 'react';

const NavAppBar = ({organisaatiot}: {organisaatiot: Organisaatio[]}) => {
    const [open, setOpen] = useState(false);

    const toggleDrawer = (newOpen: boolean) => () => {
      setOpen(newOpen);
    };
    
  var selectedOrg = '1.2.246.562.10.73999728683'
  const handleSelect = (event, nodeId) => {
    event.preventDefault
    console.log(nodeId)
    selectedOrg = nodeId
  };

  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static"   
      sx={{backgroundColor: 'white'}}>
        <Toolbar>
          <HomeIconLink />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Tähän valittu organisaatio
          </Typography>
          <IconButton
            onClick={toggleDrawer(true)}
            title="vaihda organisaatiota">
            <MenuIcon />
            </IconButton>
            <Drawer open={open} onClose={toggleDrawer(false)} anchor='right'>
                <OrganisaatioSelect organisaatiot={organisaatiot} selectedOid={selectedOrg} handleSelect={handleSelect}/>
            </Drawer>
        </Toolbar>
      </AppBar>
    </Box>
  );
};

export default NavAppBar;

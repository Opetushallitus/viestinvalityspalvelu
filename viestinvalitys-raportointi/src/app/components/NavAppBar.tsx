'use client'
import { AppBar, Box, Drawer, IconButton, Toolbar, Typography } from '@mui/material';
import HomeIconLink from './HomeIconLink';
import MenuIcon from '@mui/icons-material/Menu';
import { Organisaatio } from '../lib/types';
import OrganisaatioSelect from './OrganisaatioSelect';
import { useState } from 'react';
import useQueryParams from '../hooks/useQueryParams';

const NavAppBar = ({organisaatiot}: {organisaatiot: Organisaatio[]}) => {
    const [open, setOpen] = useState(false);
    const [selectedOrg, setSelectedOrg] = useState();
    const { setQueryParam } = useQueryParams();
    
    const toggleDrawer = (newOpen: boolean) => () => {
      setOpen(newOpen);
    };
    
  const handleSelect = (event, nodeId) => {
    console.log(nodeId)
  };

  const handleChange = (event) => {
    setSelectedOrg(event.target.value);
    setQueryParam(event.target.name, event.target.value)
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
                <OrganisaatioSelect organisaatiot={organisaatiot} selectedOid={selectedOrg} handleSelect={handleSelect} handleChange={handleChange}/>
            </Drawer>
        </Toolbar>
      </AppBar>
    </Box>
  );
};

export default NavAppBar;

'use client'
import { AppBar, Box, Toolbar } from '@mui/material';
import HomeIconLink from './HomeIconLink';
import OrganisaatioSelect from './OrganisaatioSelect';

const NavAppBar = () => {

  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static"   
      sx={{backgroundColor: 'white'}}>
        <Toolbar>
          <HomeIconLink />
          <OrganisaatioSelect />
        </Toolbar>
      </AppBar>
    </Box>
  );
};

export default NavAppBar;

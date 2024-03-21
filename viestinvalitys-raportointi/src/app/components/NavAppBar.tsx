'use client'
import { AppBar, Box, Drawer, IconButton, Toolbar, Typography } from '@mui/material';
import HomeIconLink from './HomeIconLink';
import MenuIcon from '@mui/icons-material/Menu';
import { Organisaatio } from '../lib/types';
import OrganisaatioSelect from './OrganisaatioSelect';
import { useState } from 'react';
import useQueryParams from '../hooks/useQueryParams';
import { findOrganisaatioByOid, parseExpandedParents } from '../lib/util';

const NavAppBar = ({organisaatiot}: {organisaatiot: Organisaatio[]}) => {
    const [open, setOpen] = useState(false);
    const [selectedOrg, setSelectedOrg] = useState<Organisaatio>();
    const [selectedOid, setSelectedOid] = useState<string>();
    const [expandedOids, setExpandedOids] = useState<string[]>([]);
    const { setQueryParam } = useQueryParams();
    
    const toggleDrawer = (newOpen: boolean) => () => {
      setOpen(newOpen);
    };
    
  const handleSelect = (event: any, nodeId: string) => {
    const index = expandedOids.indexOf(nodeId);
    const copyExpanded = [...expandedOids];
    if (index === -1) {
      copyExpanded.push(nodeId);
    } else {
      copyExpanded.splice(index, 1);
    }
    setExpandedOids(copyExpanded);
  };

  const handleToggle = (event:any, nodeIds:string[]) => {
    setExpandedOids(nodeIds);
  };

  const handleChange = (event:any) => {
    setSelectedOid(event.target.value);
    setQueryParam(event.target.name, event.target.value)
    setOpen(false)
    const selectedOrgTemp = findOrganisaatioByOid(organisaatiot, event.target.value)
    setSelectedOrg(selectedOrgTemp)
    setExpandedOids(parseExpandedParents(selectedOrgTemp?.parentOidPath))
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
                <OrganisaatioSelect organisaatiot={organisaatiot} selectedOid={selectedOid} expandedOids={expandedOids || []} handleSelect={handleSelect} handleChange={handleChange} handleToggle={handleToggle}/>
            </Drawer>
        </Toolbar>
      </AppBar>
    </Box>
  );
};

export default NavAppBar;

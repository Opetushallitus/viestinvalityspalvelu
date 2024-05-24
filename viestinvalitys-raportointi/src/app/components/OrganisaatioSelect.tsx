'use client';

import { useState } from 'react';
import useSwr from 'swr';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import Typography from '@mui/material/Typography';
import Drawer from '@mui/material/Drawer';
import { Organisaatio } from '../lib/types';
import OrganisaatioFilter from './OrganisaatioFilter';
import useQueryParams from '../hooks/useQueryParams';
import { useSearchParams } from 'next/navigation';
import { searchOrganisaatio } from '../lib/data';
import { collectOrgsWithMatchingName, findOrganisaatioByOid, parseExpandedParents } from '../lib/util';
import OrganisaatioHierarkia from './OrganisaatioHierarkia';

const OrganisaatioSelect = ({...props}) => {
  const [open, setOpen] = useState(false);
  const [selectedOrg, setSelectedOrg] = useState<Organisaatio>();
  const [selectedOid, setSelectedOid] = useState<string>();
  const [expandedOids, setExpandedOids] = useState<string[]>([]);
  const { setQueryParam, removeQueryParam } = useQueryParams();
  const searchParams = useSearchParams();

  const toggleDrawer = (newOpen: boolean) => () => {
    setOpen(newOpen);
  };
  
  const { data, error, isLoading } = useSwr(searchParams?.get('orgSearchStr')?.toString(), searchOrganisaatio);

  const expandSearchMatches = () => {
   if(searchParams?.get('orgSearchStr')) {
    const result: { oid: string; parentOidPath: string; }[] = []; 
    collectOrgsWithMatchingName(data, searchParams?.get('orgSearchStr') || '', result)
    const parentOids: any[] | ((prevState: string[]) => string[]) = []
    for (const r of result) { 
      parentOids.concat(parseExpandedParents(r.parentOidPath))
    }
    setExpandedOids(parentOids) 
    }
  }

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

  const handleToggle = (event: any, nodeIds: string[]) => {
    setExpandedOids(nodeIds);
  };

  const handleChange = (event: any) => {
    setSelectedOid(event.target.value);
    setQueryParam(event.target.name, event.target.value);
    setOpen(false);
    const selectedOrgTemp = findOrganisaatioByOid(
      data?.organisaatiot || [],
      event.target.value
    );
    setSelectedOrg(selectedOrgTemp);
    setExpandedOids(parseExpandedParents(selectedOrgTemp?.parentOidPath));
  };

  return (
    <>
      <Typography component="div" sx={{ ml: 2, flexGrow: 1, color: 'black' }}>
        {selectedOrg?.nimi?.fi}
      </Typography>
      <IconButton onClick={toggleDrawer(true)} title="vaihda organisaatiota">
        <MenuIcon />
      </IconButton>
      <Drawer open={open} onClose={toggleDrawer(false)} anchor="right">
        <OrganisaatioFilter handleChange={expandSearchMatches} />
        <Typography component="div">Valittu organisaatio: {}</Typography>
        {isLoading ?
        <Typography>Ladataan</Typography> : 
      <OrganisaatioHierarkia
            organisaatiot={data?.organisaatiot || []}
            selectedOid={selectedOid}
            expandedOids={expandedOids || []}
            handleSelect={handleSelect}
            handleChange={handleChange}
            handleToggle={handleToggle} />
    }
      </Drawer>
    </>
  );
};

export default OrganisaatioSelect;

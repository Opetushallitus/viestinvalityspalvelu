'use client';
/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/no-unused-vars */
import { useState } from 'react';
import { Drawer, IconButton } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { Organisaatio, OrganisaatioSearchResult } from '../lib/types';
import OrganisaatioFilter from './OrganisaatioFilter';
import useQueryParams from '../hooks/useQueryParams';
import { useSearchParams } from 'next/navigation';
import { searchOrganisaatio } from '../lib/data';
import { useQueryState } from 'nuqs';
import {
  collectOrgsWithMatchingName,
  findOrganisaatioByOid,
  parseExpandedParents,
} from '../lib/util';
import OrganisaatioHierarkia from './OrganisaatioHierarkia';
import { skipToken, useQuery } from '@tanstack/react-query';
import { Typography } from '@opetushallitus/oph-design-system';

const OrganisaatioSelect = ({ ...props }) => {
  const [open, setOpen] = useState(false);
  const [selectedOrg, setSelectedOrg] = useState<Organisaatio>();
  const [selectedOid, setSelectedOid] = useState<string>();
  const [expandedOids, setExpandedOids] = useState<string[]>([]);
  const [orgSearch, setOrgSearch] = useQueryState('orgSearchStr', {
    shallow: false,
  });
  const { setQueryParam, removeQueryParam } = useQueryParams();
  const searchParams = useSearchParams();

  const toggleDrawer = (newOpen: boolean) => () => {
    setOpen(newOpen);
  };

  const searchOrgs = async (searchStr: string): Promise<Organisaatio[]> => {
    // TODO tsekkaa että parametri löytyy
    const response = await searchOrganisaatio(searchParams?.get('orgSearchStr')?.toString() ?? '')
    return response.organisaatiot ?? []
  }

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['searchOrgs'],
    queryFn: orgSearch ? () => searchOrgs(orgSearch) : skipToken,
    enabled: !!orgSearch
  })

  const expandSearchMatches = () => {
    if (orgSearch!=null && data) {
      const result: { oid: string; parentOidPath: string }[] = [];
      collectOrgsWithMatchingName(
        data,
        searchParams?.get('orgSearchStr') ?? '',
        result,
      );
      const parentOids: any[] | ((prevState: string[]) => string[]) = [];
      for (const r of result) {
        parentOids.concat(parseExpandedParents(r.parentOidPath));
      }
      setExpandedOids(parentOids);
    }
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

  const handleToggle = (event: any, nodeIds: string[]) => {
    setExpandedOids(nodeIds);
  };

  const handleChange = (event: any) => {
    setSelectedOid(event.target.value);
    setQueryParam(event.target.name, event.target.value);
    setOpen(false);
    const selectedOrgTemp = findOrganisaatioByOid(
      data ?? [],
      event.target.value,
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
        {isLoading ? (
          <Typography>Ladataan</Typography>
        ) : (
          <OrganisaatioHierarkia
            organisaatiot={data ?? []}
            selectedOid={selectedOid}
            expandedOids={expandedOids ?? []}
            handleSelect={handleSelect}
            handleChange={handleChange}
            handleToggle={handleToggle}
          />
        )}
      </Drawer>
    </>
  );
};

export default OrganisaatioSelect;

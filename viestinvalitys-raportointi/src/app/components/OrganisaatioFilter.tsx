'use client';

import { useState } from 'react';
import useSwr from 'swr';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import useQueryParams from '../hooks/useQueryParams';
import { useDebouncedCallback } from 'use-debounce';
import FormControl from '@mui/material/FormControl';
import FormLabel from '@mui/material/FormLabel';
import TextField from '@mui/material/TextField';
import { Organisaatio } from '../lib/types';
import { findOrganisaatioByOid, parseExpandedParents } from '../lib/util';
import OrganisaatioSelect from './OrganisaatioSelect';
import { searchOrganisaatio } from '../lib/data';
import Typography from '@mui/material/Typography';

export default function OrganisaatioFilter() {
  const [open, setOpen] = useState(false);
  const [selectedOrg, setSelectedOrg] = useState<Organisaatio>();
  const [selectedOid, setSelectedOid] = useState<string>();
  const [expandedOids, setExpandedOids] = useState<string[]>([]);
  const { setQueryParam, removeQueryParam } = useQueryParams();

  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { replace } = useRouter();
  const { data, error, isLoading } = useSwr(searchParams?.get('orgSearchStr')?.toString(), searchOrganisaatio);

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback(term => {
    const params = new URLSearchParams(searchParams?.toString() || '');
    if (term) {
      params.set('orgSearchStr', term);
    } else {
      params.delete('orgSearchStr');
    }
    replace(`${pathname}?${params.toString()}`);
  }, 3000);

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
    removeQueryParam('orgSearchStr');
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
      <FormControl>
        <FormLabel>Hae organisaatiota</FormLabel>
        <TextField
          id="orgSearchStr"
          variant="outlined"
          placeholder={'Hae organisaatiota'}
          onChange={e => {
            handleTypedSearch(e.target.value);
          }}
          defaultValue={searchParams?.get('orgSearchStr')?.toString()}
        />
      </FormControl>
      {isLoading ?
        <Typography>Ladataan</Typography> : 
      <OrganisaatioSelect
        organisaatiot={data?.organisaatiot || []}
        selectedOid={selectedOid}
        expandedOids={expandedOids || []}
        handleSelect={handleSelect}
        handleChange={handleChange}
        handleToggle={handleToggle}
      />
    }
    </>
  );
}



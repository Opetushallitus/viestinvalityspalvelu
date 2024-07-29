'use client';
import { ChangeEvent, SyntheticEvent, useState } from 'react';
import { Drawer, IconButton, styled } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { Organisaatio } from '../lib/types';
import OrganisaatioFilter from './OrganisaatioFilter';
import { searchOrganisaatio } from '../lib/data';
import { useQueryState } from 'nuqs';
import {
  collectOrgsWithMatchingName,
  findOrganisaatioByOid,
  parseExpandedParents,
} from '../lib/util';
import OrganisaatioHierarkia from './OrganisaatioHierarkia';
import { useQuery } from '@tanstack/react-query';
import { Typography } from '@opetushallitus/oph-design-system';
import { NUQS_DEFAULT_OPTIONS } from '../lib/constants';

export const StyledDrawer = styled(Drawer)(({theme}) => ({
  '& .MuiDrawer-paper': {
    padding: theme.spacing(2),
  },
}));

const OrganisaatioSelect = () => {
  const [open, setOpen] = useState(false);
  const [selectedOrg, setSelectedOrg] = useState<Organisaatio>();
  const [expandedOids, setExpandedOids] = useState<string[]>([]);
  const [selectedOid, setSelectedOid] = useQueryState(
    'organisaatio',
    NUQS_DEFAULT_OPTIONS,
  );
  const [orgSearch, setOrgSearch] = useQueryState(
    'orgSearchStr',
    NUQS_DEFAULT_OPTIONS,
  );

  const toggleDrawer = (newOpen: boolean) => () => {
    setOpen(newOpen);
  };

  const searchOrgs = async (): Promise<Organisaatio[]> => {
    // kyselyä kutsutaan vain jos search-parametri on asetettu
    const response = await searchOrganisaatio(orgSearch?.toString() ?? '');
    if (response.organisaatiot) {
      expandSearchMatches(response.organisaatiot);
    }
    return response.organisaatiot ?? [];
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['searchOrgs', orgSearch],
    queryFn: () => searchOrgs(),
    enabled: Boolean(orgSearch),
  });

  const expandSearchMatches = (foundOrgs: Organisaatio[]) => {
    if (orgSearch != null && foundOrgs?.length) {
      const result: { oid: string; parentOidPath: string }[] = [];
      collectOrgsWithMatchingName(foundOrgs, orgSearch ?? '', result);
      const parentOids: Set<string> = new Set();
      for (const r of result) {
        const parents = parseExpandedParents(r.parentOidPath);
        parents.forEach((parentOid) => parentOids.add(parentOid));
      }
      const uniqueParentOids = Array.from(parentOids);
      setExpandedOids(uniqueParentOids);
    }
  };

  // TreeView-komponentin noden nagivointiklikkaus, ei varsinainen valinta
  const handleSelect = (
    event: SyntheticEvent<Element, Event>,
    nodeId: string,
  ) => {
    const index = expandedOids.indexOf(nodeId);
    const copyExpanded = [...expandedOids];
    if (index === -1) {
      copyExpanded.push(nodeId);
    } else {
      copyExpanded.splice(index, 1);
    }
    setExpandedOids(copyExpanded);
  };

  // nodet auki/kiinni
  const handleToggle = (
    event: SyntheticEvent<Element, Event>,
    nodeIds: string[],
  ) => {
    setExpandedOids(nodeIds);
  };

  // valittu organisaationode radiobuttonilla
  const handleSelectedChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSelectedOid(event.target.value);
    setOpen(false);
    const selectedOrgTemp = findOrganisaatioByOid(
      data ?? [],
      event.target.value,
    );
    setSelectedOrg(selectedOrgTemp);
    setOrgSearch(null)
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
      <StyledDrawer
        open={open}
        onClose={toggleDrawer(false)}
        anchor="right"
        sx={{ padding: 2 }}
      >
        <OrganisaatioFilter />
        <Typography component="div">
          Valittu organisaatio: {selectedOrg?.nimi.fi}
        </Typography>
        {isLoading ? (
          <Typography>Ladataan</Typography>
        ) : (
          <OrganisaatioHierarkia
            organisaatiot={data ?? []}
            selectedOid={selectedOid}
            expandedOids={expandedOids ?? []}
            handleSelect={handleSelect}
            handleChange={handleSelectedChange}
            handleToggle={handleToggle}
          />
        )}
      </StyledDrawer>
    </>
  );
};

export default OrganisaatioSelect;

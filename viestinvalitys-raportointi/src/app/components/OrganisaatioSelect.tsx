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
  translateOrgName,
} from '../lib/util';
import OrganisaatioHierarkia from './OrganisaatioHierarkia';
import { useQuery } from '@tanstack/react-query';
import { OphTypography } from '@opetushallitus/oph-design-system';
import { NUQS_DEFAULT_OPTIONS } from '../lib/constants';
import { useTranslation } from '../i18n/clientLocalization';
import { useLocale } from '../i18n/locale-provider';
import { getLocale } from '../i18n/localization';
import { ClientSpinner } from './ClientSpinner';

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
    if (response.organisaatiot?.length) {
      await expandSearchMatches(response.organisaatiot);
    }
    return response.organisaatiot ?? [];
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['searchOrgs', orgSearch],
    queryFn: () => searchOrgs(),
    enabled: Boolean(orgSearch),
  });

  // matchataan käyttäjän asiointikielen nimellä - vain kälissä näkyvät osumat
  const expandSearchMatches = async (foundOrgs: Organisaatio[]) => {
    const locale = await getLocale();
    if (orgSearch != null && foundOrgs?.length) {
      const result: { oid: string; parentOidPath: string }[] = [];
      collectOrgsWithMatchingName(foundOrgs, orgSearch ?? '', locale, result);
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
  const { t } = useTranslation();
  const lng = useLocale(); 
  return (
    <>
      <OphTypography component="div" sx={{ ml: 2, flexGrow: 1, color: 'black' }}>
        {translateOrgName(selectedOrg, lng)} 
      </OphTypography>
      <IconButton onClick={toggleDrawer(true)} title={t('organisaatio.vaihda')}>
        <MenuIcon />
      </IconButton>
      <StyledDrawer
        open={open}
        onClose={toggleDrawer(false)}
        anchor="right"
        sx={{ padding: 2 }}
      >
        <OrganisaatioFilter />
        <OphTypography component="div">
          {t('organisaatio.valittu', {organisaatioNimi: translateOrgName(selectedOrg, lng)})}
        </OphTypography>
        {isLoading ? (
          <ClientSpinner />
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

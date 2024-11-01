'use client';
import { useState } from 'react';
import { Drawer, IconButton, Stack, styled } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { LanguageCode, Organisaatio } from '../lib/types';
import OrganisaatioFilter from './OrganisaatioFilter';
import { searchOrganisaatio } from '../lib/data';
import { useQueryState } from 'nuqs';
import {
  collectOrgsWithMatchingName,
  findOrganisaatioByOid,
  parseExpandedParents,
  translateOrgName,
} from '../lib/util';
import { useQuery } from '@tanstack/react-query';
import { OphTypography } from '@opetushallitus/oph-design-system';
import { NUQS_DEFAULT_OPTIONS } from '../lib/constants';
import { ClientSpinner } from './ClientSpinner';
import { useLocale, useTranslations } from 'next-intl';
import { SimpleTreeView } from '@mui/x-tree-view';
import React from 'react';
import { OrganisaatioTree } from './OrganisaatioHierarkia';

export const StyledDrawer = styled(Drawer)(({ theme }) => ({
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

  const lng = useLocale() as LanguageCode;
  const searchOrgs = async (): Promise<Organisaatio[]> => {
    // kyselyä kutsutaan vain jos search-parametri on asetettu
    const response = await searchOrganisaatio(orgSearch?.toString() ?? '');
    if (response.organisaatiot?.length) {
      await expandSearchMatches(response.organisaatiot, lng);
    }
    return response.organisaatiot ?? [];
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { data, isLoading } = useQuery({
    queryKey: ['searchOrgs', orgSearch],
    queryFn: () => searchOrgs(),
    enabled: Boolean(orgSearch),
  });

  // matchataan käyttäjän asiointikielen nimellä - vain kälissä näkyvät osumat
  const expandSearchMatches = async (
    foundOrgs: Organisaatio[],
    locale: LanguageCode,
  ) => {
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

  // nodet auki/kiinni, parametrit ja tyypitys Muin mukaan
  const handleExpandedItemsChange = (
    event: React.SyntheticEvent,
    itemIds: string[],
  ) => {
    setExpandedOids(itemIds);
  };

  // käsitellään organisaation valinta radiobuttonilla
  const handleSelectedChange = (
    event: React.SyntheticEvent,
    itemId: string,
    isSelected: boolean,
  ) => {
    if (isSelected) {
      setSelectedOid(itemId);
      setOpen(false);
      const selectedOrgTemp = findOrganisaatioByOid(
        data ?? [],
        itemId,
      );
      setSelectedOrg(selectedOrgTemp);
      setOrgSearch(null);
      setExpandedOids(parseExpandedParents(selectedOrgTemp?.parentOidPath));
    }
  };



  const t = useTranslations();
  return (
    <>
      <OphTypography
        component="div"
        sx={{ ml: 2, flexGrow: 1, color: 'black' }}
      >
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
        <Stack spacing={2}>
          <OrganisaatioFilter />
          <OphTypography component="div">
            {t('organisaatio.valittu', {
              organisaatioNimi: translateOrgName(selectedOrg, lng),
            })}
          </OphTypography>
          {isLoading ? (
            <ClientSpinner />
          ) : (
            <SimpleTreeView
              multiSelect={false}
              aria-label={t('organisaatio.label')}
              onItemSelectionToggle={handleSelectedChange}
              onExpandedItemsChange={handleExpandedItemsChange}
              selectedItems={selectedOid}
              expandedItems={expandedOids}
              checkboxSelection={true}
            >
              {data?.map((org) => OrganisaatioTree(org))}
            </SimpleTreeView>
          )}
        </Stack>
      </StyledDrawer>
    </>
  );
};

export default OrganisaatioSelect;

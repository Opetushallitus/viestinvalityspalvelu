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
  const [organisaatioHaku, setOrganisaatioHaku] = useQueryState(
    'organisaatioHaku',
    NUQS_DEFAULT_OPTIONS,
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [seuraavatAlkaen, setSeuraavatAlkaen] = useQueryState(
    'seuraavatAlkaen',
    NUQS_DEFAULT_OPTIONS,
  );
  const t = useTranslations();
  const lng = useLocale() as LanguageCode;

  const toggleDrawer = (newOpen: boolean) => () => {
    setOpen(newOpen);
  };

  const searchOrgs = async (): Promise<Organisaatio[]> => {
    // kyselyä kutsutaan vain jos search-parametri on asetettu
    const response = await searchOrganisaatio(organisaatioHaku?.toString() ?? '');
    if (response.organisaatiot?.length) {
      await expandSearchMatches(response.organisaatiot, lng);
    }
    return response.organisaatiot ?? [];
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { data, isLoading } = useQuery({
    queryKey: ['searchOrgs', organisaatioHaku],
    queryFn: () => searchOrgs(),
    enabled: Boolean(organisaatioHaku),
  });

  // matchataan käyttäjän asiointikielen nimellä - vain kälissä näkyvät osumat
  const expandSearchMatches = async (
    foundOrgs: Organisaatio[],
    locale: LanguageCode,
  ) => {
    if (organisaatioHaku != null && foundOrgs?.length) {
      const result: { oid: string; parentOidPath: string }[] = [];
      collectOrgsWithMatchingName(foundOrgs, organisaatioHaku ?? '', locale, result);
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
      setOrganisaatioHaku(null); // nollataan hakusana
      setSeuraavatAlkaen(null); // nollataan sivutus suodatuskriteerin muuttuessa
      setExpandedOids(parseExpandedParents(selectedOrgTemp?.parentOidPath));
    }
  };

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
              {data?.map((org) => OrganisaatioTree(org, lng))}
            </SimpleTreeView>
          )}
        </Stack>
      </StyledDrawer>
    </>
  );
};

export default OrganisaatioSelect;

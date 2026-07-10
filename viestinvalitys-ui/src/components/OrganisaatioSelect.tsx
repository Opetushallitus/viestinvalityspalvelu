import React, { useState } from 'react';
import { Drawer, IconButton, Stack, styled } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { LanguageCode, Organisaatio } from '../lib/types';
import OrganisaatioFilter from './OrganisaatioFilter';
import { searchOrganisaatio } from '../lib/api';
import { useSearchParams } from 'react-router-dom';
import {
  collectOrgsWithMatchingName,
  findOrganisaatioByOid,
  parseExpandedParents,
  translateOrgName,
} from '../lib/util';
import { useQuery } from '@tanstack/react-query';
import { OphTypography } from '@opetushallitus/oph-design-system';
import { ClientSpinner } from './ClientSpinner';
import { useTranslation } from 'react-i18next';
import i18n from '../i18n';
import { SimpleTreeView } from '@mui/x-tree-view';
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
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedOid = searchParams.get('organisaatio');
  const organisaatioHaku = searchParams.get('organisaatioHaku');
  const { t } = useTranslation();
  const lng = (i18n.language as LanguageCode) || 'fi';

  const toggleDrawer = (newOpen: boolean) => () => {
    setOpen(newOpen);
  };

  const setParam = (key: string, value: string | null) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
      return next;
    });
  };

  const searchOrgs = async (): Promise<Organisaatio[]> => {
    const response = await searchOrganisaatio(organisaatioHaku?.toString() ?? '');
    if (response.organisaatiot?.length) {
      await expandSearchMatches(response.organisaatiot, lng);
    }
    return response.organisaatiot ?? [];
  };

  const { data, isLoading } = useQuery({
    queryKey: ['searchOrgs', organisaatioHaku],
    queryFn: () => searchOrgs(),
    enabled: Boolean(organisaatioHaku),
  });

  const expandSearchMatches = async (foundOrgs: Organisaatio[], locale: LanguageCode) => {
    if (organisaatioHaku != null && foundOrgs?.length) {
      const result: { oid: string; parentOidPath: string }[] = [];
      collectOrgsWithMatchingName(foundOrgs, organisaatioHaku ?? '', locale, result);
      const parentOids: Set<string> = new Set();
      for (const r of result) {
        const parents = parseExpandedParents(r.parentOidPath);
        parents.forEach((parentOid) => parentOids.add(parentOid));
      }
      setExpandedOids(Array.from(parentOids));
    }
  };

  const handleExpandedItemsChange = (_event: React.SyntheticEvent, itemIds: string[]) => {
    setExpandedOids(itemIds);
  };

  const handleSelectedChange = (
    _event: React.SyntheticEvent,
    itemId: string,
    isSelected: boolean,
  ) => {
    if (isSelected) {
      setParam('organisaatio', itemId);
      setOpen(false);
      const selectedOrgTemp = findOrganisaatioByOid(data ?? [], itemId);
      setSelectedOrg(selectedOrgTemp);
      setParam('organisaatioHaku', null);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('organisaatio', itemId);
        next.delete('organisaatioHaku');
        next.delete('seuraavatAlkaen');
        if (selectedOrgTemp) {
          setExpandedOids(parseExpandedParents(selectedOrgTemp.parentOidPath));
        }
        return next;
      });
    }
  };

  return (
    <>
      <OphTypography component="div" sx={{ ml: 2, flexGrow: 1, color: 'black' }}>
        {translateOrgName(selectedOrg, lng)}
      </OphTypography>
      <IconButton onClick={toggleDrawer(true)} title={t('organisaatio.vaihda')}>
        <MenuIcon />
      </IconButton>
      <StyledDrawer open={open} onClose={toggleDrawer(false)} anchor="right" sx={{ padding: 2 }}>
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

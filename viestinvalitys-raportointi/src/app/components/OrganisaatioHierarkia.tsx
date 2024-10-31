'use client';
import { SimpleTreeView, TreeItem } from '@mui/x-tree-view';
import { LanguageCode, Organisaatio } from '../lib/types';
import { FormControl, FormControlLabel, Radio } from '@mui/material';
import { ChangeEvent, SyntheticEvent } from 'react';
import { translateOrgName } from '../lib/util';
import { useLocale, useTranslations } from 'next-intl';

type Props = {
  organisaatiot: Organisaatio[];
  selectedOid: string | undefined;
  expandedOids: string[];
  handleSelect: (event: SyntheticEvent<Element, Event>, itemIds: string | null) => void;
  handleChange: (event: ChangeEvent<HTMLInputElement>) => void;
  handleToggle: (event: SyntheticEvent<Element, Event>, itemIds: string[]) => void;
}

const OrganisaatioHierarkia = ({
  organisaatiot,
  selectedOid,
  expandedOids,
  handleSelect,
  handleChange,
  handleToggle,
}: Props) => {
  const lng = useLocale() as LanguageCode;
  const t = useTranslations();
  const renderTree = (org: Organisaatio) => {
    if (!org) {
      return null;
    }
    return (
      <TreeItem
        key={org.oid}
        itemId={org.oid}
        label={
          <FormControl>
            <FormControlLabel
              label={translateOrgName(org, lng)} 
              control={
                <Radio
                  checked={selectedOid === org.oid}
                  name="organisaatio"
                  value={org.oid}
                  onChange={(e) => {
                    handleChange(e);
                  }}
                />
              }
            />
          </FormControl>
        }
      >
        {Array.isArray(org.children)
          ? org.children.map((node) => renderTree(node))
          : null}
      </TreeItem>
    );
  };

  return (
    <>
      <SimpleTreeView
        multiSelect={false}
        aria-label={t('organisaatio.label')}
        onSelectedItemsChange={handleSelect}
        onExpandedItemsChange={handleToggle}
        selectedItems={selectedOid}
        expandedItems={expandedOids}
      >
        {organisaatiot.map((org) => renderTree(org))}
      </SimpleTreeView>
    </>
  );
};

export default OrganisaatioHierarkia;

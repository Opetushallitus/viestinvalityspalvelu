'use client';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { TreeItem, TreeView } from '@mui/x-tree-view';
import { Organisaatio } from '../lib/types';
import { FormControl, FormControlLabel, Radio } from '@mui/material';
import { ChangeEvent, SyntheticEvent } from 'react';
import { useLocale } from '../i18n/locale-provider';
import { translateOrgName } from '../lib/util';
import { useTranslation } from '../i18n/clientLocalization';

type Props = {
  organisaatiot: Organisaatio[];
  selectedOid: string | undefined;
  expandedOids: string[];
  handleSelect: (event: SyntheticEvent<Element, Event>, nodeId: string) => void;
  handleChange: (event: ChangeEvent<HTMLInputElement>) => void;
  handleToggle: (event: SyntheticEvent<Element, Event>, nodeIds: string[]) => void;
}

const OrganisaatioHierarkia = ({
  organisaatiot,
  selectedOid,
  expandedOids,
  handleSelect,
  handleChange,
  handleToggle,
}: Props) => {
  const lng = useLocale();
  const { t } = useTranslation();
  const renderTree = (org: Organisaatio) => {
    if (!org) {
      return null;
    }
    return (
      <TreeItem
        key={org.oid}
        nodeId={org.oid}
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
      <TreeView
        multiSelect={false}
        aria-label={t('organisaatio.label')}
        defaultCollapseIcon={<ExpandMoreIcon />}
        defaultExpandIcon={<ChevronRightIcon />}
        onNodeSelect={handleSelect}
        onNodeToggle={handleToggle}
        selected={selectedOid}
        expanded={expandedOids}
      >
        {organisaatiot.map((org) => renderTree(org))}
      </TreeView>
    </>
  );
};

export default OrganisaatioHierarkia;

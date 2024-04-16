'use client';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { TreeItem, TreeView } from '@mui/x-tree-view';
import { Organisaatio } from '../lib/types';
import {
  FormControl,
  FormControlLabel,
  Radio,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useSearchParams } from 'next/navigation';

const OrganisaatioSelect = ({
  organisaatiot,
  selectedOid,
  expandedOids,
  handleSelect,
  handleChange,
  handleToggle,
}: {
  organisaatiot: Organisaatio[];
  selectedOid: string | undefined;
  expandedOids: string[];
  handleSelect: any;
  handleChange: any;
  handleToggle: any;
}) => {
  const searchParams = useSearchParams();
  const [selectedOrg, setSelectedOrg] = useState<Organisaatio>();

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
              label={org.nimi.fi}
              control={
                <Radio
                  checked={selectedOid === org.oid}
                  name="organisaatio"
                  value={org.oid}
                  onChange={e => {
                    handleChange(e);
                  }}
                />
              }
            />
          </FormControl>
        }
      >
        {Array.isArray(org.children)
          ? org.children.map(node => renderTree(node))
          : null}
      </TreeItem>
    );
  };

  return searchParams?.get('orgSearchStr') ? (
    <TreeView
    aria-label="organisaatiot"
    defaultCollapseIcon={<ExpandMoreIcon />}
    defaultExpandIcon={<ChevronRightIcon />}
    onNodeSelect={handleSelect}
    onNodeToggle={handleToggle}
    selected={selectedOid}
    expanded={expandedOids}>
    {organisaatiot.map(org => renderTree(org))}
  </TreeView>
  ) : (
    <Typography component="div">Valittu organisaatio: {selectedOrg?.nimi?.fi}</Typography>
  );
};

export default OrganisaatioSelect;

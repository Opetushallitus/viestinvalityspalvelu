'use client';
/* eslint-disable @typescript-eslint/no-explicit-any */
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { TreeItem, TreeView } from '@mui/x-tree-view';
import { Organisaatio } from '../lib/types';
import { FormControl, FormControlLabel, Radio } from '@mui/material';

const OrganisaatioHierarkia = ({
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
        aria-label="organisaatiot"
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

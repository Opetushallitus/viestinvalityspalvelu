'use client';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { TreeItem, TreeView } from '@mui/x-tree-view';
import { Organisaatio } from '../lib/types';
import { FormControl, FormControlLabel, Radio } from '@mui/material';
import { useState } from 'react';
import useQueryParams from '../hooks/useQueryParams';

const OrganisaatioSelect = ({organisaatiot, selectedOid, handleSelect}: {organisaatiot: Organisaatio[], selectedOid: string, handleSelect: any}) => {
  const [selectedOrg, setSelectedOrg] = useState();
  const { setQueryParam } = useQueryParams();

  const renderTree = (org: Organisaatio) => {
    if (!org) {
      return null;
    }
    return (
      <TreeItem key={org.oid} nodeId={org.oid} label={
        <FormControl>
          <FormControlLabel 
          label={org.nimi.fi}
          control={          
          <Radio
            checked={selectedOrg === org.oid}
            name='organisaatio'
            value={org.oid}
            onChange={(e) => {
              setSelectedOrg(e.target.value);
              setQueryParam(e.target.name, e.target.value)
            }
          }/>}/>
        </FormControl>
      }>
        {Array.isArray(org.children)
          ? org.children.map((node) => renderTree(node))
          : null}
      </TreeItem>
    );
  };

  return (
    <TreeView
      aria-label="organisaatiot"
      defaultCollapseIcon={<ExpandMoreIcon />}
      defaultExpandIcon={<ChevronRightIcon />}
      onNodeSelect={handleSelect}
      selected={selectedOid}
    >
      {organisaatiot
        .map((org) => (renderTree(org)))}
    </TreeView>
  );
}


export default OrganisaatioSelect

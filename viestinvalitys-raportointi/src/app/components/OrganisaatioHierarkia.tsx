'use client';
import { TreeItem2, TreeItem2Props, TreeItem2SlotProps } from '@mui/x-tree-view';
import { LanguageCode, Organisaatio } from '../lib/types';
import { translateOrgName } from '../lib/util';
import React from 'react';
import { RadioButtonChecked, RadioButtonUnchecked } from '@mui/icons-material';

const CustomTreeItem = React.forwardRef(function CustomTreeItem(
  props: TreeItem2Props,
  ref: React.Ref<HTMLLIElement>,
) {
  return (
    <TreeItem2
      {...props}
      ref={ref}
      slotProps= {{
          checkbox: {
            icon: <RadioButtonUnchecked />,
            checkedIcon: <RadioButtonChecked />,
          },
        } as TreeItem2SlotProps
      }
    />
  );
});

export const OrganisaatioTree = (org: Organisaatio, lng: LanguageCode) => {
  if (!org) {
    return null;
  }
  return (
    <CustomTreeItem
      itemId={org.oid}
      key={org.oid}
      label={translateOrgName(org, lng)}
    >
      {Array.isArray(org.children)
        ? org.children.map((node) => OrganisaatioTree(node, lng))
        : null}
    </CustomTreeItem>
  );
};

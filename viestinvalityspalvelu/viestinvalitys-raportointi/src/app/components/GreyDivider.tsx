'use client';
import { Divider } from "@mui/material";
import { ophColors } from "@opetushallitus/oph-design-system";

export const GreyDivider = () => {
    return (
    <Divider variant="middle" sx={{ color: ophColors.grey400, height: '2px'}} aria-hidden="true" />
    );
  };



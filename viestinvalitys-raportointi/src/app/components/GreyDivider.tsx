'use client';
import { Divider } from "@mui/material";
import { colors } from "../theme";

export const GreyDivider = () => {
    return (
    <Divider variant="middle" sx={{ color: colors.grey400, height: '2px'}} aria-hidden="true" />
    );
  };



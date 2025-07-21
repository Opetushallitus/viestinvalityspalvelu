'use client';
import { Table, TableBody, TableCell, styled } from "@mui/material";
import { ophColors } from "@opetushallitus/oph-design-system";

export const StyledTable = styled(Table)({
    width: '100%',
    borderSpacing: '0px',
  });
  
 export const StyledCell = styled(TableCell)({
    borderSpacing: '0px',
    padding: '1rem',
    textAlign: 'left',
    whiteSpace: 'pre-wrap',
    borderWidth: 0,
    'button:focus': {
      color: ophColors.blue2,
    },
  });

  
  export const StyledHeaderCell = styled(TableCell)({
    background: ophColors.white,
    verticalAlign: 'bottom',
    paddingBottom: '0.5rem',
  });
  
export const StyledTableBody = styled(TableBody)({
    '& .MuiTableRow-root': {
      '&:nth-of-type(even)': {
        backgroundColor: ophColors.grey50,
      },
      '&:hover': {
        backgroundColor: ophColors.lightBlue2,
      },
    },
  });
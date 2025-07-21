'use client';
import { Alert, Box } from '@mui/material';

const VirheAlert = ({ virheet }: { virheet?: string[] }) => {
  return virheet ? (
    <Box>
      {virheet.map((virhe: string, index) => (
        <Alert key={index} severity="error">
          {virhe}
        </Alert>
      ))}
    </Box>
  ) : (
    <></>
  );
};

export default VirheAlert;

import { Box } from '@mui/material';
import { PageContent } from './PageContent';

export const PageLayout = ({
  header,
  children,
}: {
  header: React.ReactNode;
  children: React.ReactNode;
}) => {
  return (
    <Box
      width="100%"
      display="flex"
      flexDirection="column"
      rowGap={4}
      alignItems="stretch"
    >
      {header}
      <PageContent>{children}</PageContent>
    </Box>
  );
};

'use client'
import { Avatar, Box } from "@mui/material";
import { styled } from "../theme";
import { FolderOutlined } from "@mui/icons-material";
import { ophColors } from "@opetushallitus/oph-design-system";

const Wrapper = styled(Box)({
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 2,
  });
  
  export const IconCircle = styled(Avatar)({
    backgroundColor: ophColors.grey100,
    color: ophColors.grey500,
    width: '48px',
    height: '48px',
  });
  
  export const NoResults = ({
    text,
    icon,
  }: {
    text: string;
    icon?: React.ReactNode;
  }) => {
    return (
      <Wrapper>
        <IconCircle>{icon ?? <FolderOutlined />}</IconCircle>
        <Box>{text}</Box>
      </Wrapper>
    );
  };
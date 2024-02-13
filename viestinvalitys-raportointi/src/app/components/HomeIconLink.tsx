'use client'
import { IconButton } from "@mui/material"
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined';
import Link from 'next/link';

const HomeIconLink = () => {
    return (
        <IconButton aria-label="home" href="/" size="large" component={Link} sx={{ border: "1px solid", borderRadius: "5px", width: 30, height: 30}}>
            <HomeOutlinedIcon />
        </IconButton>
    )
  }

  export default HomeIconLink
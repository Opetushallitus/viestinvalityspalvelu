'use client'
import { CheckCircle, Error, Warning, WatchLater } from '@mui/icons-material';
import { Status, LahetyksenVastaanottoTila } from "../lib/types"
import { getLahetysStatus, lahetyksenStatus } from '../lib/util';
import { Box } from '@mui/material';


export const StatusIcon = ({status}: {status: string}) => {
    if (status===Status.EPAONNISTUI) {
      return (<Error />)  
    }
    if (status===Status.KESKEN) {
      return (<WatchLater />)  
    }
    if (status===Status.ONNISTUI) {
      return (<CheckCircle />)  
    }
    return (<Warning />)
  }

export const LahetysStatus = ({tilat}: {tilat: LahetyksenVastaanottoTila[]}) => {
  console.log(tilat)
  const status = getLahetysStatus(tilat.map(tila => tila.vastaanottotila))
  return (
    <Box display="flex" alignItems="center"> <StatusIcon status={status} />&nbsp;{lahetyksenStatus(tilat)}</Box>
  )
}

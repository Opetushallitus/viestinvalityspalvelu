'use client'
import { CheckCircle, Error, WatchLater } from '@mui/icons-material';
import { Status, VastaanottajaTila } from "./lib/types"
import { getLahetysStatus } from './lib/util';


const LahetysStatus = ({tilat}: {tilat: VastaanottajaTila[]}) => {
    const status = getLahetysStatus(tilat.map(tila => tila.vastaanottotila))
    if (status===Status.EPAONNISTUI) {
      return (<Error />)  
    }
    if (status===Status.KESKEN) {
      return (<WatchLater />)  
    }
    if (status===Status.ONNISTUI) {
      return (<CheckCircle />)  
    }
  }

export default LahetysStatus
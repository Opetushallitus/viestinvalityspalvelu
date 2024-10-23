'use client';
import { useEffect, useState } from "react";
import { OphTypography } from "@opetushallitus/oph-design-system";
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';


// päivämääräformatoinnin hydraatio-ongelman taklaus 
// ks. https://nextjs.org/docs/messages/react-hydration-error#solution-1-using-useeffect-to-run-on-the-client-only
export default function LocalDateTime({date}: {date: string}) {
  const [isClient, setIsClient] = useState(false);

  useEffect(() => {
    setIsClient(true);
  }, []);

  return <OphTypography>{isClient ? toFormattedDateTimeString(date) : ''}</OphTypography>;
}

function toFormattedDateTimeString(value: string): string {
  try {
    dayjs.extend(utc)
    dayjs.extend(timezone)
    return dayjs(value).tz('Europe/Helsinki').format('DD.MM.YYYY HH:mm')
  } catch (error) {
    console.warn(
      'Caught error when trying to format date, returning empty string',
    );
    return '';
  }
}
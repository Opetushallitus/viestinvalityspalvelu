'use client';
import { useEffect, useState } from "react";
import { format, toZonedTime } from 'date-fns-tz';
import { OphTypography } from "@opetushallitus/oph-design-system";

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
    const zonedDate = toZonedTime(new Date(value), 'Europe/Helsinki');
    return format(zonedDate, 'd.M.yyyy HH:mm', {
      timeZone: 'Europe/Helsinki',
    });
  } catch (error) {
    console.warn(
      'Caught error when trying to format date, returning empty string',
    );
    return '';
  }
}





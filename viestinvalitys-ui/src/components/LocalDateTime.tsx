import { useEffect, useState } from 'react';
import { OphTypography } from '@opetushallitus/oph-design-system';
import moment from 'moment-timezone';

export default function LocalDateTime({ date }: { date: string }) {
  const [isClient, setIsClient] = useState(false);

  useEffect(() => {
    setIsClient(true);
  }, []);

  return <OphTypography>{isClient ? toFormattedDateTimeString(date) : ''}</OphTypography>;
}

function toFormattedDateTimeString(value: string): string {
  try {
    return moment(value).tz('Europe/Helsinki').format('DD.MM.YYYY HH:mm');
  } catch (error) {
    console.warn(
      `Caught error when trying to format date, returning empty string. Error: ${error}`,
    );
    return '';
  }
}

import { Link } from '@mui/material';
import { OphTypography } from '@opetushallitus/oph-design-system';
import { useTranslation } from 'react-i18next';
import { Liite } from '../lib/types';

const Liitteet = ({
  liitteet,
  viestiTunniste,
  downloadEnabled,
}: {
  liitteet?: Liite[];
  viestiTunniste: string;
  downloadEnabled: boolean;
}) => {
  const { t } = useTranslation();
  if (!liitteet || liitteet.length === 0) {
    return <></>;
  }
  return (
    <div>
      <OphTypography variant="h5" component="h3">
        {t('viesti.liitteet')}
      </OphTypography>
      <ul>
        {liitteet.map((liite) => (
          <li key={liite.tunniste}>
            {downloadEnabled ? (
              <Link
                href={`/viestinvalityspalvelu/v1/download/liite?viestiTunniste=${viestiTunniste}&liiteTunniste=${liite.tunniste}`}
              >
                {liite.nimi}
              </Link>
            ) : (
              liite.nimi
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Liitteet;

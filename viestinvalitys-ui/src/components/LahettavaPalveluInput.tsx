import { OphFormControl } from './OphFormControl';
import { SelectChangeEvent } from '@mui/material';
import { OphSelect } from '@opetushallitus/oph-design-system';
import { useTranslation } from 'react-i18next';

const LahettavaPalveluSelect = ({
  labelId,
  value: selectedPalvelu,
  options,
  onChange,
}: {
  labelId: string;
  value: string;
  options: string[];
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const palveluOptions = options.map((palvelu) => {
    return { value: palvelu, label: palvelu };
  });
  const { t } = useTranslation();
  return (
    <OphSelect
      labelId={labelId}
      id="palvelu-select"
      value={selectedPalvelu ?? ''}
      onChange={onChange}
      placeholder={t('yleinen.valitse')}
      options={palveluOptions}
      clearable
    />
  );
};

export const LahettavaPalveluInput = ({
  value,
  onChange,
  palvelut,
}: {
  value: string | null;
  onChange: (e: SelectChangeEvent) => void;
  palvelut: string[];
}) => {
  const { t } = useTranslation();

  return (
    <OphFormControl
      label={t('lahetykset.haku.lahettava-palvelu')}
      sx={{ flexGrow: 4, flex: '1 0 250px', textAlign: 'left' }}
      renderInput={({ labelId }) => (
        <LahettavaPalveluSelect
          labelId={labelId}
          value={value ?? ''}
          options={palvelut}
          onChange={onChange}
        />
      )}
    />
  );
};

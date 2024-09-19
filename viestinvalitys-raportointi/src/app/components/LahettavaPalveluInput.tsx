'use client';
import { OphFormControl } from './OphFormControl';
import { useTranslation } from '../i18n/clientLocalization';
import { SelectChangeEvent } from '@mui/material';
import { OphSelect } from '@opetushallitus/oph-design-system';

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

  return (
    <OphSelect
      labelId={labelId}
      id="palvelu-select"
      value={selectedPalvelu ?? ''}
      onChange={onChange}
      options={palveluOptions}
      clearable
    />
  );
};

export const LahettavaPalveluInput = ({
  value,
  onChange,
  palvelut
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
  palvelut: string[];
}) => {
  const { t } = useTranslation();

  return (
    <OphFormControl
      label={t('lahetykset.haku.lahettava-palvelu')}
      sx={{ flex: '1 0 180px', textAlign: 'left' }}
      renderInput={({ labelId }) => (
        <LahettavaPalveluSelect
          labelId={labelId}
          value={value}
          options={palvelut}
          onChange={onChange}
        />
      )}
    />
  );
};

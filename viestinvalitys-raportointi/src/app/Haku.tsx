'use client';
import {
  Box,
  InputAdornment,
  OutlinedInput,
  SelectChangeEvent,
} from '@mui/material';
import { useDebouncedCallback } from 'use-debounce';
import { useQueryState } from 'nuqs';
import { Search } from '@mui/icons-material';
import { NUQS_DEFAULT_OPTIONS } from './lib/constants';
import { useTranslation } from './i18n/clientLocalization';
import { LahettavaPalveluInput } from './components/LahettavaPalveluInput';
import { OphFormControl } from './components/OphFormControl';
import { OphSelect } from '@opetushallitus/oph-design-system';

const HakukenttaSelect = ({
  labelId,
  value: selectedHakukentta,
  onChange,
}: {
  labelId: string;
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const { t } = useTranslation();
  return (
    <OphSelect
      labelId={labelId}
      id="hakukentta-select"
      value={selectedHakukentta ?? ''}
      onChange={onChange}
      options={[
        { value: 'vastaanottaja', label: t('lahetykset.haku.vastaanottaja') },
        { value: 'lahettaja', label: t('lahetykset.haku.lahettaja') },
        { value: 'viesti', label: t('lahetykset.haku.otsikko-sisalto') },
      ]}
      clearable
      displayEmpty={true}
    />
  );
};

const HakukenttaInput = ({
  value,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const { t } = useTranslation();
  return (
    <OphFormControl
      label={t('lahetykset.haku.mista-haetaan')}
      sx={{ flex: '1 0 180px', textAlign: 'left' }}
      renderInput={({ labelId }) => (
        <HakukenttaSelect value={value} onChange={onChange} labelId={labelId} />
      )}
    />
  );
};

export default function Haku({lahettavatPalvelut}: {lahettavatPalvelut: string[]}) {
  const [selectedHakukentta, setSelectedHakukentta] = useQueryState(
    'hakukentta',
    NUQS_DEFAULT_OPTIONS,
  );
  const [hakusana, setHakusana] = useQueryState(
    'hakusana',
    NUQS_DEFAULT_OPTIONS,
  );
  const [palvelu, setPalvelu] = useQueryState('palvelu', NUQS_DEFAULT_OPTIONS);

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback((term) => {
    // päivitetään kun minimipituus täyttyy
    if (term.length > 4 || term) {
      setHakusana(term);
    } else if (term.length == 0) {
      setHakusana(null); // kentän tyhjäys
    }
  }, 3000);
  const { t } = useTranslation();
  return (
    <Box>
      <Box
        display="flex"
        flexDirection="row"
        justifyContent="stretch"
        gap={2}
        marginBottom={2}
        flexWrap="wrap"
        alignItems="flex-end"
      >
        <HakukenttaInput
          value={selectedHakukentta ?? ''}
          onChange={(e) => setSelectedHakukentta(e.target.value)}
        />
        <OphFormControl
          label={t('lahetykset.hae')}
          sx={{ flexGrow: 4, minWidth: '180px', textAlign: 'left' }}
          renderInput={({ labelId }) => {
            return (
              <OutlinedInput
                id="haku-search"
                name="haku-search"
                inputProps={{ 'aria-labelledby': labelId }}
                defaultValue={hakusana}
                onChange={(e) => {
                  handleTypedSearch(e.target.value);
                }}
                autoFocus={true}
                type="text"
                endAdornment={
                  <InputAdornment position="end">
                    <Search />
                  </InputAdornment>
                }
              />
            );
          }}
        />
      </Box>
      <Box
        display="flex"
        flexDirection="row"
        justifyContent="stretch"
        gap={2}
        marginBottom={2}
        flexWrap="wrap"
        alignItems="flex-end"
      >
        <LahettavaPalveluInput
          value={palvelu}
          onChange={(e) => setPalvelu(e.target.value)} 
          palvelut={lahettavatPalvelut} />
      </Box>
    </Box>
  );
}

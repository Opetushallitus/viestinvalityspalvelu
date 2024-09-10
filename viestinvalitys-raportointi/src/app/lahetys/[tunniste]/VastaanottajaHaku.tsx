'use client';
import {
  Box,
  FormControl,
  FormLabel,
  InputAdornment,
  MenuItem,
  OutlinedInput,
  Select,
  SelectChangeEvent,
} from '@mui/material';
import { Search } from '@mui/icons-material';
import { useDebouncedCallback } from 'use-debounce';
import { parseAsString, useQueryState, useQueryStates } from 'nuqs';
import { NUQS_DEFAULT_OPTIONS } from '@/app/lib/constants';
import { useTranslation } from '@/app/i18n/clientLocalization';

const TilaSelect = ({
  value: selectedTila,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const { t } = useTranslation();
  return (
    <Select
      labelId="tila-select-label"
      name="tila-select"
      value={selectedTila ?? ''}
      onChange={onChange}
      displayEmpty={true}
    >
      <MenuItem value="">{t('yleinen.valitse')}</MenuItem>
      <MenuItem value="epaonnistui">{t('lahetykset.haku.epaonnistui')}</MenuItem>
      <MenuItem value="kesken">{t('lahetykset.haku.kesken')}</MenuItem>
    </Select>
  );
};

const TilaInput = ({
  value,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const { t } = useTranslation();
  return (
    <FormControl sx={{ flex: '1 0 180px', textAlign: 'left' }}>
      <FormLabel id="tila-select-label">{t('lahetykset.haku.tila')}</FormLabel>
      <TilaSelect value={value} onChange={onChange} />
    </FormControl>
  );
};

export default function VastaanottajaHaku() {
  const [vastaanottajaHaku, setVastaanottajahaku] = useQueryStates(
    {
      hakukentta: parseAsString.withDefault(''),
      hakusana: parseAsString.withDefault('')
    }, NUQS_DEFAULT_OPTIONS
  )
  const [tila, setTila] = useQueryState('tila', NUQS_DEFAULT_OPTIONS);

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback((term) => {
    setVastaanottajahaku({
      hakusana: term,
      hakukentta: 'vastaanottaja'
    })
  }, 3000);

  return (
    <Box
      display="flex"
      flexDirection="row"
      justifyContent="stretch"
      gap={2}
      marginBottom={2}
      flexWrap="wrap"
      alignItems="flex-end"
    >
      <FormControl
        sx={{
          flexGrow: 4,
          minWidth: '180px',
          textAlign: 'left',
        }}
      >
        <OutlinedInput
          id="haku-search"
          name="haku-search"
          defaultValue={vastaanottajaHaku.hakusana ?? ''}
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
      </FormControl>
      <TilaInput value={tila ?? ''} onChange={(e) => setTila(e.target.value)} />
    </Box>
  );
}

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
import { useQueryState } from 'nuqs';
import { NUQS_DEFAULT_OPTIONS } from '@/app/lib/constants';
import { useTranslations } from 'next-intl';
import { useHasChanged } from '@/app/hooks/useHasChanged';
import { useEffect } from 'react';

const TilaSelect = ({
  value: selectedTila,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const t = useTranslations();
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
  const t = useTranslations();
  return (
    <FormControl sx={{ flex: '1 0 180px', textAlign: 'left' }}>
      <FormLabel id="tila-select-label">{t('lahetykset.haku.tila')}</FormLabel>
      <TilaSelect value={value} onChange={onChange} />
    </FormControl>
  );
};

export default function VastaanottajaHaku() {
  const [tila, setTila] = useQueryState('tila', NUQS_DEFAULT_OPTIONS);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [alkaen, setAlkaen] = useQueryState('alkaen', NUQS_DEFAULT_OPTIONS);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [hakusana, setHakusana] = useQueryState(
    'hakusana',
    NUQS_DEFAULT_OPTIONS,
  );
  const tilaChanged = useHasChanged(tila);
  const hakusanaChanged = useHasChanged(hakusana);

  useEffect(() => {
    if (hakusanaChanged || tilaChanged) {
      setAlkaen(null);
    }
  }, [hakusanaChanged, tilaChanged, setAlkaen]);

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback((term) => {
    // päivitetään kun minimipituus täyttyy
    if (term.length >= 5) {
      setHakusana(term);
    } else if (term.length == 0) {
      setHakusana(null); // kentän tyhjäys
    }
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
          defaultValue={hakusana ?? ''}
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

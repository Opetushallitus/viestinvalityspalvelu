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
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

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
  const [searchParams, setSearchParams] = useSearchParams();
  const tila = searchParams.get('tila');
  const hakusana = searchParams.get('hakusana');

  const setParam = (key: string, value: string | null) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
      next.delete('alkaen');
      return next;
    });
  };

  const handleTypedSearch = useDebouncedCallback((term: string) => {
    if (term.length >= 5) {
      setParam('hakusana', term);
    } else if (term.length === 0) {
      setParam('hakusana', null);
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
      <TilaInput value={tila ?? ''} onChange={(e) => setParam('tila', e.target.value || null)} />
    </Box>
  );
}

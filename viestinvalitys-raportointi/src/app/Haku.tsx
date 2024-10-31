'use client';
import {
  Box,
  DialogActions,
  InputAdornment,
  OutlinedInput,
  SelectChangeEvent,
} from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import {
  LocalizationProvider,
  PickersActionBarProps,
} from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import 'dayjs/locale/fi';
import 'dayjs/locale/sv';
import 'dayjs/locale/en';
import { useDebouncedCallback } from 'use-debounce';
import { useQueryState } from 'nuqs';
import { useTranslations } from 'next-intl';
import { Search } from '@mui/icons-material';
import { NUQS_DEFAULT_OPTIONS } from './lib/constants';
import { LahettavaPalveluInput } from './components/LahettavaPalveluInput';
import { OphFormControl } from './components/OphFormControl';
import {
  OphButton,
  ophColors,
  OphSelect,
} from '@opetushallitus/oph-design-system';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { useHasChanged } from './hooks/useHasChanged';

function CustomActionBar(props: PickersActionBarProps) {
  const { onAccept, onClear, className } = props;
  const t = useTranslations();
  return (
    <DialogActions className={className} sx={{ mb: 2, mr: 2 }}>
      <OphButton variant="contained" onClick={onAccept}>
        {t('yleinen.ok')}
      </OphButton>
      <OphButton variant="outlined" onClick={onClear}>
        {t('yleinen.tyhjenna')}
      </OphButton>
    </DialogActions>
  );
}

const HakukenttaSelect = ({
  labelId,
  value: selectedHakukentta,
  onChange,
}: {
  labelId: string;
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const t = useTranslations();
  return (
    <OphSelect
      labelId={labelId}
      id="hakukentta-select"
      value={selectedHakukentta ?? ''}
      onChange={onChange}
      placeholder={t('yleinen.valitse')}
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
  const t = useTranslations();
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

export default function Haku({
  lahettavatPalvelut,
  locale,
}: {
  lahettavatPalvelut: string[];
  locale: string;
}) {
  const [selectedHakukentta, setSelectedHakukentta] = useQueryState(
    'hakukentta',
    NUQS_DEFAULT_OPTIONS,
  );
  const [hakusana, setHakusana] = useQueryState(
    'hakusana',
    NUQS_DEFAULT_OPTIONS,
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [seuraavatAlkaen, setSeuraavatAlkaen] = useQueryState(
    'seuraavatAlkaen',
    NUQS_DEFAULT_OPTIONS,
  );
  const [palvelu, setPalvelu] = useQueryState('palvelu', NUQS_DEFAULT_OPTIONS);
  const [hakuAlkaen, setHakuAlkaen] = useQueryState(
    'hakuAlkaen',
    NUQS_DEFAULT_OPTIONS,
  );
  const [hakuPaattyen, setHakuPaattyen] = useQueryState(
    'hakuPaattyen',
    NUQS_DEFAULT_OPTIONS,
  );
  const [calendarErrors, setCalendarErrors] = useState<Array<string>>([]);

  const hakusanaChanged = useHasChanged(hakusana);
  const palveluChanged = useHasChanged(palvelu);
  const hakuAlkaenChanged = useHasChanged(hakuAlkaen);
  const hakuPaattyenChanged = useHasChanged(hakuPaattyen);

  useEffect(() => {
    if (
      hakusanaChanged ||
      palveluChanged ||
      hakuAlkaenChanged ||
      hakuPaattyenChanged
    ) {
      setSeuraavatAlkaen(null);
    }
  }, [
    hakusanaChanged,
    palveluChanged,
    hakuAlkaenChanged,
    hakuPaattyenChanged,
    setSeuraavatAlkaen,
  ]);

  const handleAlkuDateTimeChange = (value: dayjs.Dayjs | null) => {
    setHakuAlkaen(value?.toISOString() ?? null);
  };

  const handleLoppuDateTimeChange = (value: dayjs.Dayjs | null) => {
    if (hakuAlkaen && value && !dayjs(hakuAlkaen).isBefore(value)) {
      setCalendarErrors([t('error.virheellinen-aikavali')]);
    } else {
      setCalendarErrors([]);
      setHakuPaattyen(value?.toISOString() ?? null);
    }
  };

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback((term) => {
    // päivitetään kun minimipituus täyttyy
    if (term.length >= 5) {
      setHakusana(term);
    } else if (term.length == 0) {
      setHakusana(null); // kentän tyhjäys
    }
  }, 3000);
  const t = useTranslations();

  const calendarSlotProps = {
    textField: { placeholder: t('yleinen.valitse'), error: false },
    // 1. Change the layout of the month selector.
    calendarHeader: {
      sx: {
        '& .MuiIconButton-edgeEnd': {
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
        },
        '& .MuiIconButton-edgeStart': {
          position: 'absolute',
          right: 0,
          top: 0,
          bottom: 0,
        },
      },
    },
    // 2. Change the arrow icons styles.
    leftArrowIcon: {
      sx: { border: '1px solid', borderRadius: '50%', color: ophColors.blue2 },
    },
    rightArrowIcon: {
      sx: { border: '1px solid', borderRadius: '50%' },
    },
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale={locale}>
      <Box>
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'row',
            justifyContent: 'stretch',
            gap: 2,
            marginBottom: 2,
            flexWrap: 'wrap',
            alignItems: 'flex-end',
          }}
        >
          <HakukenttaInput
            value={selectedHakukentta ?? ''}
            onChange={(e) => setSelectedHakukentta(e.target.value)}
          />
          <OphFormControl
            label={t('lahetykset.hae')}
            sx={{ flexGrow: 4, minWidth: '180px', textAlign: 'left' }}
            helperText={t('lahetykset.hakukentta-ohje')}
            renderInput={({ labelId }) => {
              return (
                <OutlinedInput
                  id="haku-search"
                  name="haku-search"
                  inputProps={{ 'aria-labelledby': labelId }}
                  defaultValue={hakusana}
                  key={hakusana}
                  onChange={(e) => {
                    handleTypedSearch(e.target.value);
                  }}
                  autoFocus={true}
                  placeholder={t('lahetykset.hakukentta-placeholder')}
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
          justifyContent="space-between"
          gap={2}
          marginBottom={2}
          flexWrap="nowrap"
          alignItems="flex-end"
        >
          <LahettavaPalveluInput
            value={palvelu}
            onChange={(e) => setPalvelu(e.target.value)}
            palvelut={lahettavatPalvelut}
          />
          <OphFormControl
            label={t('lahetykset.haku.alkaen')}
            sx={{ textAlign: 'left', flexShrink: 2 }}
            renderInput={({ labelId }) => {
              return (
                <DateTimePicker
                  disableFuture={true}
                  value={dayjs(hakuAlkaen)}
                  onChange={(newValue) => handleAlkuDateTimeChange(newValue)}
                  aria-labelledby={labelId}
                  timeSteps={{ minutes: 1 }}
                  slots={{
                    actionBar: CustomActionBar,
                  }}
                  slotProps={calendarSlotProps}
                />
              );
            }}
          />
          <OphFormControl
            label={t('lahetykset.haku.paattyen')}
            sx={{ minWidth: '180px', textAlign: 'left' }}
            errorMessages={calendarErrors}
            renderInput={({ labelId }) => {
              return (
                <DateTimePicker
                  disableFuture={true}
                  value={dayjs(hakuPaattyen)}
                  onChange={(newValue) => handleLoppuDateTimeChange(newValue)}
                  aria-labelledby={labelId}
                  timeSteps={{ minutes: 1 }}
                  slots={{
                    actionBar: CustomActionBar,
                  }}
                  slotProps={calendarSlotProps}
                />
              );
            }}
          />
        </Box>
      </Box>
    </LocalizationProvider>
  );
}

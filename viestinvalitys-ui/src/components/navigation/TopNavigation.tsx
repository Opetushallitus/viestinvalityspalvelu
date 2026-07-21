import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import './TopNavigation.css';

/**
 * Replicates henkilo-ui's TopNavigation tab bar. Currently a single tab that
 * links to the root (Lähetykset), which lets the user return to LahetyksetPage
 * from a detail page. `end` keeps the tab "active" only on the exact root path.
 */
export const TopNavigation = () => {
  const { t } = useTranslation();
  return (
    <div className="oph-ds-navigation" role="navigation">
      <NavLink
        to="/"
        end
        className={({ isActive }) => `oph-ds-navlink ${isActive ? 'active' : ''}`}
      >
        {t('navigointi.lahetykset')}
      </NavLink>
    </div>
  );
};

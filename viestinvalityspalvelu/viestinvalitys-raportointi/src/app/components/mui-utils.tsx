// eslint-disable-next-line @typescript-eslint/ban-ts-comment
import { forwardRef } from 'react';

// MUI:sta (Emotionista) puuttuu styled-componentsin .attrs
// Tällä voi asettaa oletus-propsit ilman, että tarvii luoda välikomponenttia
export function withDefaultProps<P>(
    Component: React.ComponentType<P>,
    defaultProps: Partial<P>,
    displayName = 'ComponentWithDefaultProps',
  ) {
    const ComponentWithDefaultProps = forwardRef<
      React.ComponentRef<React.ComponentType<P>>,
      P
      // @ts-expect-error: Tässä kohtaa tyypitys menee hankalaksi
    >((props, ref) => <Component {...defaultProps} {...props} ref={ref} />);
  
    ComponentWithDefaultProps.displayName = displayName;
    return ComponentWithDefaultProps;
  }
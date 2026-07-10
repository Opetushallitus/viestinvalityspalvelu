import React, { forwardRef } from 'react';

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

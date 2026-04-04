import React from 'react';
import { render, screen } from '@testing-library/react-native';
import AppText from '../Text';
import { TestWrapper } from '../../../test-utils';

const renderText = (props: Partial<React.ComponentProps<typeof AppText>> = {}) => {
  const { children = 'Hello World', ...rest } = props;
  return render(<AppText {...rest}>{children}</AppText>, { wrapper: TestWrapper });
};

describe('AppText', () => {
  it('renders children text', () => {
    renderText({ children: 'Test content' });
    expect(screen.getByText('Test content')).toBeTruthy();
  });

  it('renders with title variant', () => {
    renderText({ variant: 'title', children: 'Title Text' });
    expect(screen.getByText('Title Text')).toBeTruthy();
  });

  it('renders with body variant', () => {
    renderText({ variant: 'body', children: 'Body Text' });
    expect(screen.getByText('Body Text')).toBeTruthy();
  });

  it('renders with caption variant', () => {
    renderText({ variant: 'caption', children: 'Caption Text' });
    expect(screen.getByText('Caption Text')).toBeTruthy();
  });

  it('renders with subtitle variant', () => {
    renderText({ variant: 'subtitle', children: 'Subtitle' });
    expect(screen.getByText('Subtitle')).toBeTruthy();
  });

  it('renders with label variant', () => {
    renderText({ variant: 'label', children: 'Label' });
    expect(screen.getByText('Label')).toBeTruthy();
  });
});

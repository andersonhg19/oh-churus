import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import ParentCategoryPicker from '../ParentCategoryPicker';
import { ThemeProvider } from '../../../contexts/ThemeContext';
import * as AuthContext from '../../../contexts/AuthContext';
import { categoryService } from '../../../services/categoryService';

jest.mock('../../../services/categoryService', () => ({
  categoryService: { getTree: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

const tree = [
  { id: '10', name: 'Vivienda', type: 'EXPENSE', icon: 'home', children: [
    { id: '11', name: 'Arriendo', type: 'EXPENSE', children: [] },
  ] },
  { id: '20', name: 'Salario', type: 'INCOME', children: [] },
];

describe('ParentCategoryPicker', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (categoryService.getTree as jest.Mock).mockResolvedValue({ correct: true, object: tree });
  });

  it('renders label and the "no parent" placeholder', () => {
    const { getByText } = render(
      <Wrapper><ParentCategoryPicker value={null} onChange={jest.fn()} /></Wrapper>
    );
    expect(getByText('Categoría padre')).toBeTruthy();
    expect(getByText('Sin padre (categoría raíz)')).toBeTruthy();
  });

  it('expands, loads the tree and selects an option', async () => {
    const onChange = jest.fn();
    const { getByText } = render(
      <Wrapper><ParentCategoryPicker value={null} onChange={onChange} filterType="EXPENSE" /></Wrapper>
    );
    // open the panel by pressing the selector (shows the placeholder when no value)
    fireEvent.press(getByText('Sin padre (categoría raíz)'));
    await waitFor(() => expect(categoryService.getTree).toHaveBeenCalledWith('1'));
    await waitFor(() => expect(getByText('Vivienda')).toBeTruthy());
    fireEvent.press(getByText('Vivienda'));
    expect(onChange).toHaveBeenCalledWith('10');
  });

  it('selects "no parent" from the panel', async () => {
    const onChange = jest.fn();
    const { getByText, getAllByText } = render(
      <Wrapper><ParentCategoryPicker value={'10'} onChange={onChange} /></Wrapper>
    );
    // before load, options are empty so the selector still shows the placeholder
    fireEvent.press(getByText('Sin padre (categoría raíz)'));
    await waitFor(() => expect(categoryService.getTree).toHaveBeenCalled());
    await waitFor(() => expect(getByText('Salario')).toBeTruthy());
    const noParent = getAllByText('Sin padre (categoría raíz)');
    fireEvent.press(noParent[noParent.length - 1]);
    expect(onChange).toHaveBeenCalledWith(null);
  });
});

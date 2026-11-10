import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import PlanScreen from '../plan/PlanScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { planService } from '../../services/planService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, [cb]);
  },
}));

jest.mock('../../services/planService', () => ({
  planService: { quincena: jest.fn(), calendar: jest.fn(), responsibilities: jest.fn(), projection: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '4', name: 'Samy', email: 's@b.com', budgetStartDay: 28 },
  token: 'tok', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
} as any);

const item = (name: string, amount: number, status: string, kind = 'PAYMENT', income = false) => ({
  scheduledMovementId: name.length, name, date: '2026-11-01', amount, status, kind, income,
  categoryId: 1, categoryName: 'X', shared: true, payerUserId: 4, payerName: 'Samy',
});

const ventana = {
  start: '2026-11-01', end: '2026-11-14', grossIncome: 3300000, deductions: 500000, netIncome: 2800000,
  payments: 190000, paymentsPaid: 0, paymentsLeft: 190000, savings: 150000, provisions: 600000,
  leftover: 1860000, tight: false,
  incomeItems: [item('Salario Samy Q1', 3300000, 'PREVISTO', 'PAYMENT', true)],
  deductionItems: [item('Computador Q1', 500000, 'PREVISTO', 'DEDUCTION')],
  paymentItems: [item('Gym', 160000, 'PENDIENTE')],
  savingItems: [], provisionItems: [item('Mercado principal', 600000, 'PROVISION', 'PROVISION')],
};

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;

describe('PlanScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (planService.quincena as jest.Mock).mockResolvedValue({
      correct: true, object: {
        today: '2026-11-05', payDates: ['2026-11-01', '2026-11-15'], hasIncome: true,
        current: ventana, next: { ...ventana, start: '2026-11-15', end: '2026-11-30' },
        overdueFromBefore: [],
        installments: [{ scheduledMovementId: 9, name: 'Computador Q1', installmentAmount: 500000, totalInstallments: 7,
          paidInstallments: 0, remainingInstallments: 7, remainingTotal: 3500000, nextDate: '2026-11-01',
          endDate: '2027-05-31', monthlyFreed: 500000, kind: 'DEDUCTION' }],
        alerts: [{ type: 'PROXIMO', message: 'Gym vence el 2026-11-01' }],
      },
    });
    (planService.responsibilities as jest.Mock).mockResolvedValue({
      correct: true, object: { periodStart: '2026-10-28', periodEnd: '2026-11-27', sharedTotal: 2490000,
        people: [{ userId: 3, name: 'Andy', isMe: false, income: 7960000, sharedTotal: 2300000, sharedPaid: 0,
          sharePct: 92.4, sharedByKind: {}, items: [] }] },
    });
  });

  it('muestra la quincena con el descuento aparte y las cuotas', async () => {
    const { queryByTestId, getAllByText, getByText } = render(<Wrapper><PlanScreen /></Wrapper>);
    await waitFor(() => expect(getByText(/Gym vence/)).toBeTruthy());
    /* "Libre para gastar hoy" salia del saldo de las cuentas, que se retiraron. */
    expect(queryByTestId('disponible-hoy')).toBeNull();
    expect(getAllByText('Ingreso efectivo').length).toBeGreaterThan(0);
    expect(getAllByText(/Descuentos de nómina/).length).toBeGreaterThan(0);
    expect(getByText(/Al terminar se liberan/)).toBeTruthy();
    expect(getByText(/Gym vence/)).toBeTruthy();
  });

  it('la vista Hogar dice quien paga que', async () => {
    const { getByText } = render(<Wrapper><PlanScreen /></Wrapper>);
    await waitFor(() => expect(planService.quincena).toHaveBeenCalled());
    fireEvent.press(getByText('Hogar'));
    await waitFor(() => expect(getByText('Andy')).toBeTruthy());
    expect(getByText(/Paga de lo común \(92.4%\)/)).toBeTruthy();
  });
});

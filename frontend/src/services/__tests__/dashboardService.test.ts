import api from '../api';
import { dashboardService } from '../dashboardService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;

describe('dashboardService', () => {
  beforeEach(() => mockPost.mockReset());

  it('getSummary posts with userId and budgetStartDay', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: { balance: 100 } } });
    const result = await dashboardService.getSummary('1', 15);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/dashboard/summary', { userId: '1', budgetStartDay: 15 });
    expect(result.correct).toBe(true);
  });

  it('getByCategory posts correctly', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [] } });
    await dashboardService.getByCategory('1', 1);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/dashboard/by-category', { userId: '1', budgetStartDay: 1 });
  });

  it('getTrend posts correctly', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [] } });
    await dashboardService.getTrend('1', 1);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/dashboard/trend', { userId: '1', budgetStartDay: 1 });
  });

  it('getPending posts correctly', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [] } });
    await dashboardService.getPending('1', 1);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/dashboard/pending', { userId: '1', budgetStartDay: 1 });
  });
});

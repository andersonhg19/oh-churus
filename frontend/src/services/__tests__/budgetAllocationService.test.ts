import api from '../api';
import { budgetAllocationService } from '../budgetAllocationService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;
const BASE = '/BUDGET-SERVICE/oh-churus/v1/budget-allocation';

describe('budgetAllocationService', () => {
  beforeEach(() => mockPost.mockReset().mockResolvedValue({ data: { correct: true } }));

  it('save posts allocation data', async () => {
    const data = { userId: '1', categoryId: 20, amount: 500000, budgetStartDay: 1, notes: 'x' };
    await budgetAllocationService.save(data);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/save`, data);
  });

  it('list posts with referenceDate', async () => {
    await budgetAllocationService.list('1', 15, '2026-03-15');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/list`, { userId: '1', budgetStartDay: 15, referenceDate: '2026-03-15' });
  });

  it('summary posts and omits referenceDate when absent', async () => {
    await budgetAllocationService.summary('1', 1);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/summary`, { userId: '1', budgetStartDay: 1, referenceDate: undefined });
  });

  it('delete posts to delete endpoint', async () => {
    await budgetAllocationService.delete(7);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/delete/7`);
  });
});

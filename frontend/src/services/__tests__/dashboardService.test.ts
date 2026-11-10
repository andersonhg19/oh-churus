import api from '../api';
import { dashboardService } from '../dashboardService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;

describe('dashboardService', () => {
  beforeEach(() => mockPost.mockReset());

  it('getPending posts correctly', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [] } });
    await dashboardService.getPending('1', 1);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/dashboard/pending', { userId: '1', budgetStartDay: 1 });
  });
});

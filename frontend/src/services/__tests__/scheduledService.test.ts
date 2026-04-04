import api from '../api';
import { scheduledService } from '../scheduledService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;

describe('scheduledService', () => {
  beforeEach(() => mockPost.mockReset());

  it('save posts to save endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await scheduledService.save({ name: 'Rent', userId: '1', categoryId: '2', frequency: 'MONTHLY' });
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/save', expect.objectContaining({ name: 'Rent' }));
  });

  it('getById posts to get endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: { id: '1' } } });
    await scheduledService.getById('1');
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/get/1');
  });

  it('getAll posts with filter', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: { list: [] } } });
    await scheduledService.getAll({ userId: '1', page: 0, size: 10 });
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/all', { userId: '1', page: 0, size: 10 });
  });

  it('delete posts to delete endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await scheduledService.delete('1');
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/delete/1');
  });

  it('generatePending posts with userId and budgetStartDay', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: 3 } });
    const result = await scheduledService.generatePending('1', 15);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/generate-pending', { userId: '1', budgetStartDay: 15 });
    expect(result.object).toBe(3);
  });

  it('frequencyList posts to frequency-list endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [{ key: 'MONTHLY', name: 'Mensual' }] } });
    const result = await scheduledService.frequencyList();
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/frequency-list');
    expect(result.correct).toBe(true);
  });
});

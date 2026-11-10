import api from '../api';
import { movementService } from '../movementService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;

describe('movementService', () => {
  beforeEach(() => mockPost.mockReset());

  it('save posts to save endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await movementService.save({ userId: '1', categoryId: '2', amount: 100, date: '2026-01-01' });
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/movements/save', expect.objectContaining({ userId: '1' }));
  });

  it('getAll posts with filter', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: { list: [] } } });
    await movementService.getAll({ userId: '1', page: 0, size: 10 });
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/movements/all', { userId: '1', page: 0, size: 10 });
  });

  it('delete posts to delete endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await movementService.delete('5');
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/movements/delete/5');
  });

  it('confirm posts to confirm endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await movementService.confirm('3');
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/movements/confirm/3', {});
  });
});

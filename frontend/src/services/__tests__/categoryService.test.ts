import api from '../api';
import { categoryService } from '../categoryService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;

describe('categoryService', () => {
  beforeEach(() => mockPost.mockReset());

  it('save posts to save endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await categoryService.save({ name: 'Test', type: 'INCOME', userId: '1' });
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/categories/save', { name: 'Test', type: 'INCOME', userId: '1' });
  });

  it('getAll posts with filter', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: { list: [] } } });
    await categoryService.getAll({ userId: '1', page: 0, size: 10 });
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/categories/all', { userId: '1', page: 0, size: 10 });
  });

  it('getTree posts with userId', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [] } });
    await categoryService.getTree('1');
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/categories/tree', { userId: '1' });
  });

  it('delete posts to delete endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true } });
    await categoryService.delete('1');
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/categories/delete/1');
  });
});

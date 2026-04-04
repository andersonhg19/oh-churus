import api from '../api';
import { authService } from '../authService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;

describe('authService', () => {
  beforeEach(() => mockPost.mockReset());

  it('login posts to correct endpoint with credentials', async () => {
    const mockResponse = { data: { correct: true, object: { token: 't', userId: '1', name: 'A', email: 'a@b.com' } } };
    mockPost.mockResolvedValueOnce(mockResponse);
    const result = await authService.login('a@b.com', 'pass');
    expect(mockPost).toHaveBeenCalledWith('/AUTH-SERVICE/oh-churus/v1/auth/login', { email: 'a@b.com', password: 'pass' });
    expect(result).toEqual(mockResponse.data);
  });

  it('register posts to correct endpoint', async () => {
    const mockResponse = { data: { correct: true, object: { token: 't' } } };
    mockPost.mockResolvedValueOnce(mockResponse);
    const result = await authService.register({ name: 'N', email: 'e@e.com', password: 'p' });
    expect(mockPost).toHaveBeenCalledWith('/AUTH-SERVICE/oh-churus/v1/auth/register', { name: 'N', email: 'e@e.com', password: 'p' });
    expect(result).toEqual(mockResponse.data);
  });

  it('getUser posts to correct endpoint', async () => {
    const mockResponse = { data: { correct: true, object: { id: '1', name: 'A' } } };
    mockPost.mockResolvedValueOnce(mockResponse);
    const result = await authService.getUser('1');
    expect(mockPost).toHaveBeenCalledWith('/AUTH-SERVICE/oh-churus/v1/users/get/1');
    expect(result).toEqual(mockResponse.data);
  });

  it('updateUser posts to save endpoint', async () => {
    const mockResponse = { data: { correct: true, object: { id: '1' } } };
    mockPost.mockResolvedValueOnce(mockResponse);
    const result = await authService.updateUser({ id: '1', budgetStartDay: 15 });
    expect(mockPost).toHaveBeenCalledWith('/AUTH-SERVICE/oh-churus/v1/users/save', { id: '1', budgetStartDay: 15 });
    expect(result).toEqual(mockResponse.data);
  });

  it('getAllUsers posts to all endpoint with filter', async () => {
    const mockResponse = { data: { correct: true, object: { list: [], page: 0, size: 10, totalPage: 0 } } };
    mockPost.mockResolvedValueOnce(mockResponse);
    const result = await authService.getAllUsers({ page: 0, size: 10 });
    expect(mockPost).toHaveBeenCalledWith('/AUTH-SERVICE/oh-churus/v1/users/all', { page: 0, size: 10 });
    expect(result).toEqual(mockResponse.data);
  });
});

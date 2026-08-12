import api from '../api';
import { householdService } from '../householdService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;
const BASE = '/BUDGET-SERVICE/oh-churus/v1/household';

describe('householdService', () => {
  beforeEach(() => mockPost.mockReset().mockResolvedValue({ data: { correct: true } }));

  it('create posts name and userId', async () => {
    const r = await householdService.create('Familia', '1');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/create`, { name: 'Familia', userId: '1' });
    expect(r.correct).toBe(true);
  });

  it('addMember posts householdId and userId', async () => {
    await householdService.addMember(100, 2);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/add-member`, { householdId: 100, userId: 2 });
  });

  it('inviteByEmail posts householdId and email, never an id', async () => {
    // Se invita por correo: el id de fila de la base de datos no lo conoce nadie.
    await householdService.inviteByEmail(100, 'pareja@correo.com');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/invite`, {
      householdId: 100,
      email: 'pareja@correo.com',
    });
  });

  it('removeMember posts householdId and userId', async () => {
    await householdService.removeMember(100, 2);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/remove-member`, { householdId: 100, userId: 2 });
  });

  it('getByUser posts userId', async () => {
    await householdService.getByUser('1');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/by-user`, { userId: '1' });
  });
});

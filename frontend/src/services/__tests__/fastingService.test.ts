import api from '../api';
import { fastingService } from '../fastingService';

jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.Mock;
const BASE = '/FASTING-SERVICE/oh-churus/v1/fasting';

describe('fastingService', () => {
  beforeEach(() => mockPost.mockReset().mockResolvedValue({ data: { correct: true } }));

  it('getPlan posts userId', async () => {
    await fastingService.getPlan('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/plan/get`, { userId: '3' });
  });

  it('savePlan posts data', async () => {
    await fastingService.savePlan({ userId: '3', planType: 'PLAN_16_8' });
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/plan/save`, { userId: '3', planType: 'PLAN_16_8' });
  });

  it('getPresets posts empty body', async () => {
    await fastingService.getPresets();
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/plan/presets`, {});
  });

  it('startFasting posts userId and startTime', async () => {
    await fastingService.startFasting('3', '2026-03-15T20:00:00');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/session/start`, { userId: '3', startTime: '2026-03-15T20:00:00' });
  });

  it('startFasting omits startTime when not provided', async () => {
    await fastingService.startFasting('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/session/start`, { userId: '3', startTime: undefined });
  });

  it('stopFasting posts userId and endTime', async () => {
    await fastingService.stopFasting('3', '2026-03-16T12:00:00');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/session/stop`, { userId: '3', endTime: '2026-03-16T12:00:00' });
  });

  it('cancelFasting posts userId', async () => {
    await fastingService.cancelFasting('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/session/cancel`, { userId: '3' });
  });

  it('getActiveSession posts userId', async () => {
    await fastingService.getActiveSession('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/session/active`, { userId: '3' });
  });

  it('editSession posts session data', async () => {
    await fastingService.editSession(5, '2026-03-15T08:00:00', '2026-03-16T00:00:00');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/session/edit`, {
      sessionId: 5, startTime: '2026-03-15T08:00:00', endTime: '2026-03-16T00:00:00',
    });
  });

  it('getHistory posts period data', async () => {
    await fastingService.getHistory('3', 15, '2026-03-20');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/history/by-period`, {
      userId: '3', budgetStartDay: 15, referenceDate: '2026-03-20',
    });
  });

  it('getSummary posts period data', async () => {
    await fastingService.getSummary('3', 1);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/history/summary`, {
      userId: '3', budgetStartDay: 1, referenceDate: undefined,
    });
  });

  it('getAchievements posts userId', async () => {
    await fastingService.getAchievements('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/achievements`, { userId: '3' });
  });

  it('getWaterToday posts userId', async () => {
    await fastingService.getWaterToday('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/water/today`, { userId: '3' });
  });

  it('addWater posts glasses (default 1)', async () => {
    await fastingService.addWater('3');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/water/add`, { userId: '3', glasses: 1 });
    await fastingService.addWater('3', 3);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/water/add`, { userId: '3', glasses: 3 });
  });

  it('setWaterGoal posts goal', async () => {
    await fastingService.setWaterGoal('3', 10);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/water/set-goal`, { userId: '3', goalGlasses: 10 });
  });
});

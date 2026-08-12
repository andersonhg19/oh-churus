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

  it('generatePending devuelve lo creado y lo propuesto por separado', async () => {
    // El contrato dejo de ser una lista pelada cuando aparecio el tope de
    // materializacion: lo atrasado de mas no se crea, se propone.
    mockPost.mockResolvedValueOnce({
      data: {
        correct: true,
        object: {
          created: [{ id: 'm1' }],
          proposals: [{ scheduledMovementId: '9', periodStart: '2026-07-01' }],
          proposalsTotal: 12,
          needsReview: true,
        },
      },
    });
    const result = await scheduledService.generatePending('1', 15);
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/generate-pending', { userId: '1', budgetStartDay: 15 });
    expect(result.object?.created).toHaveLength(1);
    expect(result.object?.proposalsTotal).toBe(12);
    expect(result.object?.needsReview).toBe(true);
  });

  it('materialize manda solo cuales, nunca el importe ni la fecha', async () => {
    // Si el cuerpo pudiera dictar importe y fecha, "aceptar una propuesta"
    // seria una via para crear el movimiento que se quisiera.
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [{ id: 'm1' }] } });
    await scheduledService.materialize([{ scheduledMovementId: '9', periodStart: '2026-07-01' }]);
    expect(mockPost).toHaveBeenCalledWith(
      '/BUDGET-SERVICE/oh-churus/v1/scheduled/materialize',
      { occurrences: [{ scheduledMovementId: '9', periodStart: '2026-07-01' }] },
    );
  });

  it('frequencyList posts to frequency-list endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { correct: true, object: [{ key: 'MONTHLY', name: 'Mensual' }] } });
    const result = await scheduledService.frequencyList();
    expect(mockPost).toHaveBeenCalledWith('/BUDGET-SERVICE/oh-churus/v1/scheduled/frequency-list');
    expect(result.correct).toBe(true);
  });
});

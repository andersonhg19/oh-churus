import ICON_MAP from '../iconMap';
import { tieneDibujo } from '../../components/atoms/Icono';

/*
 * Cada icono que se puede elegir en Configuracion tiene que tener su dibujo de
 * linea: si no, esa categoria se ve con emoji en unas pantallas y con icono en
 * otras, que es justo lo que se reporto.
 */
describe('los iconos de las categorias', () => {
  it('todo icono elegible tiene su dibujo', () => {
    const sinDibujo = Object.keys(ICON_MAP).filter((k) => !tieneDibujo(k));
    expect(sinDibujo).toEqual([]);
  });
});

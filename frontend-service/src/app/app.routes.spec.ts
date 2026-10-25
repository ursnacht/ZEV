import { routes } from './app.routes';
import { AuthGuard } from './guards/auth.guard';
import { MesspunktEmpfangComponent } from './components/messpunkt-empfang/messpunkt-empfang.component';

describe('app.routes', () => {
  describe('messpunkt-empfang', () => {
    it('should protect the route with AuthGuard and einheit:read', () => {
      // Specs/Messpunkt-Empfang.md, Abschnitt 3 "Sicherheit & API"
      const route = routes.find(r => r.path === 'messpunkt-empfang');

      expect(route).toBeDefined();
      expect(route!.component).toBe(MesspunktEmpfangComponent);
      expect(route!.canActivate).toEqual([AuthGuard]);
      expect(route!.data?.['permissions']).toEqual(['einheit:read']);
    });
  });
});

import { GrantedRoles, hasAnyPermission, hasPermission } from './permissions';

/**
 * Tests der gemeinsamen Permission-Auswertung (`Specs/Nebenkosten/Nebenkosten.md`, FR-3a).
 *
 * Diese Funktionen sind die einzige Stelle, an der im Frontend über eine Berechtigung entschieden
 * wird — `AuthGuard` (Route) und `PermissionDirective` (Template) teilen sie sich. Ein Fehler hier
 * wirkt an zwei Orten gleichzeitig, und zwar in beide Richtungen: entweder verschwindet ein
 * Menüpunkt für Berechtigte, oder ein Unberechtigter sieht ihn und wird beim Klick abgewiesen.
 */
describe('permissions', () => {

  const rollen = (opts: {
    realm?: string[];
    resource?: Record<string, string[]>;
  } = {}): GrantedRoles => ({
    realmRoles: opts.realm ?? [],
    resourceRoles: opts.resource ?? {}
  });

  describe('hasPermission', () => {

    it('should grant when the permission is a realm role', () => {
      expect(hasPermission(rollen({ realm: ['nebenkosten:manage'] }), 'nebenkosten:manage'))
        .toBe(true);
    });

    it('should grant when the permission is a resource role', () => {
      // Permissions können als Client-Rolle statt als Realm-Rolle ankommen; beide Quellen zählen.
      expect(hasPermission(
        rollen({ resource: { 'zev-frontend': ['nebenkosten:manage'] } }),
        'nebenkosten:manage'
      )).toBe(true);
    });

    it('should search all clients, not only the first', () => {
      expect(hasPermission(
        rollen({
          resource: {
            account: ['view-profile'],
            'zev-frontend': ['einstellungen:write']
          }
        }),
        'einstellungen:write'
      )).toBe(true);
    });

    it('should deny when the permission is absent', () => {
      expect(hasPermission(
        rollen({ realm: ['einheit:read'], resource: { 'zev-frontend': ['tarife:manage'] } }),
        'nebenkosten:manage'
      )).toBe(false);
    });

    it('should deny when the user has no roles at all', () => {
      expect(hasPermission(rollen(), 'nebenkosten:manage')).toBe(false);
    });

    it('should deny a client whose role list is empty', () => {
      expect(hasPermission(rollen({ resource: { 'zev-frontend': [] } }), 'nebenkosten:manage'))
        .toBe(false);
    });

    it('should require an exact match, not a prefix', () => {
      // Sonst öffnete `nebenkosten:read` die Tür für `nebenkosten:manage` — die Permissions
      // teilen sich systematisch ihr Präfix, deshalb ist das hier kein theoretischer Fall.
      expect(hasPermission(rollen({ realm: ['nebenkosten'] }), 'nebenkosten:manage')).toBe(false);
      expect(hasPermission(rollen({ realm: ['nebenkosten:manageX'] }), 'nebenkosten:manage'))
        .toBe(false);
      expect(hasPermission(rollen({ realm: ['nebenkosten:read'] }), 'nebenkosten:manage'))
        .toBe(false);
    });

    it('should be case sensitive', () => {
      // Keycloak-Rollennamen sind case sensitive; eine tolerante Prüfung wäre eine stille
      // Ausweitung der Berechtigung.
      expect(hasPermission(rollen({ realm: ['Nebenkosten:Manage'] }), 'nebenkosten:manage'))
        .toBe(false);
    });
  });

  describe('hasAnyPermission', () => {

    it('should grant when one of several permissions matches', () => {
      expect(hasAnyPermission(
        rollen({ realm: ['einheit:write'] }),
        ['nebenkosten:manage', 'einheit:write']
      )).toBe(true);
    });

    it('should grant when the match comes from a resource role', () => {
      expect(hasAnyPermission(
        rollen({ resource: { 'zev-frontend': ['einheit:write'] } }),
        ['nebenkosten:manage', 'einheit:write']
      )).toBe(true);
    });

    it('should deny when none of the permissions matches', () => {
      expect(hasAnyPermission(
        rollen({ realm: ['einheit:read'] }),
        ['nebenkosten:manage', 'einheit:write']
      )).toBe(false);
    });

    it('should grant for an empty list — no requirement', () => {
      // Entspricht dem AuthGuard bei einer Route ohne `data.permissions`: Wer angemeldet ist,
      // darf. Die beiden Stellen müssen hier gleich entscheiden.
      expect(hasAnyPermission(rollen(), [])).toBe(true);
    });

    it('should grant when the list is null or undefined', () => {
      // Route-Daten sind optional typisiert; ohne diese Absicherung wäre der Zugriff auf
      // `.length` ein Laufzeitfehler mitten in der Guard-Auswertung.
      expect(hasAnyPermission(rollen(), null as unknown as string[])).toBe(true);
      expect(hasAnyPermission(rollen(), undefined as unknown as string[])).toBe(true);
    });

    it('should deny a single requested permission the user lacks', () => {
      expect(hasAnyPermission(rollen({ realm: ['einheit:read'] }), ['nebenkosten:manage']))
        .toBe(false);
    });
  });
});

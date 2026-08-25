import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { PermissionDirective } from './permission.directive';

/**
 * Tests der Struktur-Direktive `*appPermission` (`Specs/Nebenkosten/Nebenkosten.md`, FR-3a).
 *
 * Die Direktive entscheidet, ob ein Menüpunkt überhaupt gerendert wird. Sie ist keine
 * Sicherheitsgrenze — das ist der `AuthGuard` und serverseitig `@PreAuthorize` —, aber sie muss
 * mit dem Guard **übereinstimmen**: Ein sichtbarer Eintrag, der beim Klick auf die Startseite
 * zurückwirft, ist das unangenehmste Ergebnis einer Abweichung.
 */
describe('PermissionDirective', () => {

  /** Kein `createSpyObj`: Die Direktive liest Felder (`realmAccess`), keine Methoden. */
  const keycloakMit = (opts: {
    realmRoles?: string[];
    resourceAccess?: Record<string, { roles?: string[] }>;
  }): Keycloak => ({
    realmAccess: opts.realmRoles ? { roles: opts.realmRoles } : undefined,
    resourceAccess: opts.resourceAccess
  } as unknown as Keycloak);

  const baue = <T>(host: new () => T, keycloak: Keycloak): ComponentFixture<T> => {
    TestBed.configureTestingModule({
      imports: [host as never],
      providers: [{ provide: Keycloak, useValue: keycloak }]
    });
    const fixture = TestBed.createComponent(host);
    fixture.detectChanges();
    return fixture;
  };

  afterEach(() => TestBed.resetTestingModule());

  // ==================== Eine einzelne Permission ====================

  @Component({
    standalone: true,
    imports: [PermissionDirective],
    template: `<li class="eintrag" *appPermission="'nebenkosten:manage'">Nebenkosten</li>`
  })
  class EinzelHost {}

  it('should render the content when the permission is a realm role', () => {
    const fixture = baue(EinzelHost, keycloakMit({ realmRoles: ['nebenkosten:manage'] }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Nebenkosten');
  });

  it('should render the content when the permission is a resource role', () => {
    const fixture = baue(EinzelHost, keycloakMit({
      resourceAccess: { 'zev-frontend': { roles: ['nebenkosten:manage'] } }
    }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeTruthy();
  });

  it('should not render the content when the permission is missing', () => {
    const fixture = baue(EinzelHost, keycloakMit({ realmRoles: ['einheit:read'] }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeNull();
  });

  // ==================== Mehrere Permissions ====================

  @Component({
    standalone: true,
    imports: [PermissionDirective],
    template: `<li class="eintrag"
                   *appPermission="['nebenkosten:manage', 'einstellungen:write']">Bereich</li>`
  })
  class MehrfachHost {}

  it('should render when one of several permissions is granted', () => {
    const fixture = baue(MehrfachHost, keycloakMit({ realmRoles: ['einstellungen:write'] }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeTruthy();
  });

  it('should not render when none of several permissions is granted', () => {
    const fixture = baue(MehrfachHost, keycloakMit({ realmRoles: ['einheit:read'] }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeNull();
  });

  // ==================== Fehlende Token-Bestandteile ====================

  it('should not render and not throw when the token carries no roles at all', () => {
    // Zustand direkt nach einem Token-Refresh ohne Rollen-Claims: Die Direktive darf nicht
    // werfen, sonst reisst sie den Aufbau der ganzen Navigation mit.
    const fixture = baue(EinzelHost, keycloakMit({}));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeNull();
  });

  it('should tolerate a client entry without a roles array', () => {
    const fixture = baue(EinzelHost, keycloakMit({
      resourceAccess: { 'zev-frontend': {} }
    }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeNull();
  });

  it('should fall back to resource roles when realmAccess is absent', () => {
    const fixture = baue(EinzelHost, keycloakMit({
      resourceAccess: { 'zev-frontend': { roles: ['nebenkosten:manage'] } }
    }));

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeTruthy();
  });

  // ==================== Bewusst nicht reaktiv ====================

  /**
   * Hält den dokumentierten Entscheid fest: Die Direktive wertet **einmal** in `ngOnInit` aus,
   * anders als `*appFeature`.
   *
   * Die Rollen eines Benutzers ändern sich innerhalb einer Sitzung nicht — eine neue
   * Rollenzuweisung wirkt erst nach erneutem Anmelden, weil sie im Token steht. Wer hier später
   * Reaktivität einbaut, soll sehen, dass das eine Verhaltensänderung ist und keine Korrektur.
   */
  it('should not re-evaluate when the token changes after the first render', () => {
    const keycloak = keycloakMit({ realmRoles: ['einheit:read'] });
    const fixture = baue(EinzelHost, keycloak);
    expect(fixture.nativeElement.querySelector('.eintrag')).toBeNull();

    (keycloak as { realmAccess?: { roles: string[] } }).realmAccess = {
      roles: ['nebenkosten:manage']
    };
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.eintrag')).toBeNull();
  });
});

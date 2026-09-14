import { Routes } from '@angular/router';
import { StartseiteComponent } from './components/startseite/startseite.component';
import { EinheitListComponent } from './components/einheit-list/einheit-list.component';
import { MesswerteUploadComponent } from './components/messwerte-upload/messwerte-upload.component';
import { SolarCalculationComponent } from './components/solar-calculation/solar-calculation.component';
import { MesswerteChartComponent } from './components/messwerte-chart/messwerte-chart.component';
import { StatistikComponent } from './components/statistik/statistik.component';
import { RechnungenComponent } from './components/rechnungen/rechnungen.component';
import { TarifListComponent } from './components/tarif-list/tarif-list.component';
import { MieterListComponent } from './components/mieter-list/mieter-list.component';
import { TarifpositionListComponent } from './components/tarifposition-list/tarifposition-list.component';
import { EinstellungenComponent } from './components/einstellungen/einstellungen.component';
import { DesignSystemShowcaseComponent } from './components/design-system-showcase/design-system-showcase.component';
import { LizenzenComponent } from './components/lizenzen/lizenzen.component';
import { TranslationEditorComponent } from './components/translation-editor/translation-editor.component';
import { DebitorkontrolleListComponent } from './components/debitorkontrolle-list/debitorkontrolle-list.component';
import { SystemmeldungenComponent } from './components/systemmeldungen/systemmeldungen.component';

import { AuthGuard } from './guards/auth.guard';
import { FeatureFlagGuard } from './guards/feature-flag.guard';
import { NebenkostenTarifpositionenComponent } from './components/nebenkosten-tarifpositionen/nebenkosten-tarifpositionen.component';
import { NebenkostenAbrechnungComponent } from './components/nebenkosten-abrechnung/nebenkosten-abrechnung.component';
import { EinspeisesteuerungComponent } from './components/einspeisesteuerung/einspeisesteuerung.component';

export const routes: Routes = [
  { path: '', redirectTo: '/startseite', pathMatch: 'full' },
  { path: 'startseite', component: StartseiteComponent, canActivate: [AuthGuard] },
  { path: 'upload', component: MesswerteUploadComponent, canActivate: [AuthGuard, FeatureFlagGuard], data: { permissions: ['messwerte:write'], featureFlag: 'MESSWERTE_UPLOAD' } },
  { path: 'einheiten', component: EinheitListComponent, canActivate: [AuthGuard], data: { permissions: ['einheit:read'] } },
  { path: 'solar-calculation', component: SolarCalculationComponent, canActivate: [AuthGuard], data: { permissions: ['messwerte:write'] } },
  { path: 'chart', component: MesswerteChartComponent, canActivate: [AuthGuard], data: { permissions: ['messwerte:read'] } },
  { path: 'statistik', component: StatistikComponent, canActivate: [AuthGuard], data: { permissions: ['statistik:read'] } },
  { path: 'systemmeldungen', component: SystemmeldungenComponent, canActivate: [AuthGuard], data: { permissions: ['systemmeldungen:read'] } },
  { path: 'rechnungen', component: RechnungenComponent, canActivate: [AuthGuard], data: { permissions: ['rechnungen:manage'] } },
  { path: 'debitoren', component: DebitorkontrolleListComponent, canActivate: [AuthGuard], data: { permissions: ['debitoren:manage'] } },
  { path: 'tarife', component: TarifListComponent, canActivate: [AuthGuard], data: { permissions: ['tarife:manage'] } },
  { path: 'mieter', component: MieterListComponent, canActivate: [AuthGuard], data: { permissions: ['mieter:manage'] } },
  { path: 'tarifpositionen', component: TarifpositionListComponent, canActivate: [AuthGuard], data: { permissions: ['rechnungen:manage'] } },
  // Nebenkostenabrechnung (Specs/Nebenkosten/). Das Menue verweist direkt auf die Abrechnung
  // (FR-3); /nebenkosten wird weiterhin beantwortet, weil die URL getippt und verlinkt wird - es
  // gibt keine Wildcard-Route, ein unbekannter Pfad ergaebe einen Router-Fehler.
  // /nebenkosten/tarifpositionen bleibt erreichbar, steht aber nicht mehr im Menue: Die Seite ist
  // ein Geruest ohne Inhalt (FR-4).
  { path: 'nebenkosten', redirectTo: '/nebenkosten/abrechnung', pathMatch: 'full' },
  { path: 'nebenkosten/tarifpositionen', component: NebenkostenTarifpositionenComponent, canActivate: [AuthGuard, FeatureFlagGuard], data: { permissions: ['nebenkosten:manage'], featureFlag: 'NEBENKOSTENABRECHNUNG' } },
  { path: 'nebenkosten/abrechnung', component: NebenkostenAbrechnungComponent, canActivate: [AuthGuard, FeatureFlagGuard], data: { permissions: ['nebenkosten:manage'], featureFlag: 'NEBENKOSTENABRECHNUNG' } },
  // Einspeisesteuerung (Specs/Einspeisesteuerung.md): Trockenlauf, schaltet nichts. Dieselbe
  // Permission wie die Preiszeitreihe, mit der sie sich die Datengrundlage teilt.
  { path: 'einspeisesteuerung', component: EinspeisesteuerungComponent, canActivate: [AuthGuard, FeatureFlagGuard], data: { permissions: ['tarife:manage'], featureFlag: 'EINSPEISESTEUERUNG' } },
  { path: 'design-system', component: DesignSystemShowcaseComponent, canActivate: [AuthGuard] },
  { path: 'einstellungen', component: EinstellungenComponent, canActivate: [AuthGuard], data: { permissions: ['einstellungen:write'] } },
  { path: 'translations', component: TranslationEditorComponent, canActivate: [AuthGuard], data: { permissions: ['translations:manage'] } },
  { path: 'lizenzen', component: LizenzenComponent, canActivate: [AuthGuard], data: { permissions: ['lizenzen:read'] } }
];

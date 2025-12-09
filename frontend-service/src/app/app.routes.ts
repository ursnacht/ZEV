import { Routes } from '@angular/router';
import { EinheitListComponent } from './components/einheit-list/einheit-list.component';
import { MesswerteUploadComponent } from './components/messwerte-upload/messwerte-upload.component';
import { SolarCalculationComponent } from './components/solar-calculation/solar-calculation.component';
import { MesswerteChartComponent } from './components/messwerte-chart/messwerte-chart.component';
import { StatistikComponent } from './components/statistik/statistik.component';
import { DesignSystemShowcaseComponent } from './components/design-system-showcase/design-system-showcase.component';
import { TranslationEditorComponent } from './components/translation-editor/translation-editor.component';

import { AuthGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/chart', pathMatch: 'full' },
  { path: 'upload', component: MesswerteUploadComponent, canActivate: [AuthGuard], data: { roles: ['zev', 'zev_admin'] } },
  { path: 'einheiten', component: EinheitListComponent, canActivate: [AuthGuard], data: { roles: ['zev', 'zev_admin'] } },
  { path: 'solar-calculation', component: SolarCalculationComponent, canActivate: [AuthGuard], data: { roles: ['zev', 'zev_admin'] } },
  { path: 'chart', component: MesswerteChartComponent, canActivate: [AuthGuard], data: { roles: ['zev'] } },
  { path: 'statistik', component: StatistikComponent, canActivate: [AuthGuard], data: { roles: ['zev'] } },
  { path: 'design-system', component: DesignSystemShowcaseComponent, canActivate: [AuthGuard], data: { roles: ['zev'] } },
  { path: 'translations', component: TranslationEditorComponent, canActivate: [AuthGuard], data: { roles: ['zev_admin'] } }
];

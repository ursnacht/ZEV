import { Routes } from '@angular/router';
import { EinheitListComponent } from './components/einheit-list/einheit-list.component';
import { MesswerteUploadComponent } from './components/messwerte-upload/messwerte-upload.component';
import { SolarCalculationComponent } from './components/solar-calculation/solar-calculation.component';
import { MesswerteChartComponent } from './components/messwerte-chart/messwerte-chart.component';
import { DesignSystemShowcaseComponent } from './components/design-system-showcase/design-system-showcase.component';

import { AuthGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/chart', pathMatch: 'full' },
  { path: 'upload', component: MesswerteUploadComponent, canActivate: [AuthGuard] },
  { path: 'einheiten', component: EinheitListComponent, canActivate: [AuthGuard] },
  { path: 'solar-calculation', component: SolarCalculationComponent, canActivate: [AuthGuard] },
  { path: 'chart', component: MesswerteChartComponent, canActivate: [AuthGuard] },
  { path: 'design-system', component: DesignSystemShowcaseComponent, canActivate: [AuthGuard] }
];

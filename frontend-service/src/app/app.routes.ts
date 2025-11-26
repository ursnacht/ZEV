import { Routes } from '@angular/router';
import { EinheitListComponent } from './components/einheit-list/einheit-list.component';
import { MesswerteUploadComponent } from './components/messwerte-upload/messwerte-upload.component';
import { SolarCalculationComponent } from './components/solar-calculation/solar-calculation.component';
import { MesswerteChartComponent } from './components/messwerte-chart/messwerte-chart.component';

export const routes: Routes = [
  { path: '', redirectTo: '/upload', pathMatch: 'full' },
  { path: 'upload', component: MesswerteUploadComponent },
  { path: 'einheiten', component: EinheitListComponent },
  { path: 'solar-calculation', component: SolarCalculationComponent },
  { path: 'chart', component: MesswerteChartComponent }
];

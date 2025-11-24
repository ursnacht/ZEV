import { Routes } from '@angular/router';
import { EinheitListComponent } from './components/einheit-list/einheit-list.component';
import { MesswerteUploadComponent } from './components/messwerte-upload/messwerte-upload.component';

export const routes: Routes = [
  { path: '', redirectTo: '/upload', pathMatch: 'full' },
  { path: 'upload', component: MesswerteUploadComponent },
  { path: 'einheiten', component: EinheitListComponent }
];

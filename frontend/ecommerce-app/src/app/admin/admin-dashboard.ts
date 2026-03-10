import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminLayoutComponent } from '../shared/layout/admin-layout/admin-layout.component';
import { CardComponent } from '../shared/components/card/card.component';
import { StatCardComponent } from '../shared/components/stat-card/stat-card.component';
import { ChartCardComponent } from '../shared/components/chart-card/chart-card.component';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    AdminLayoutComponent,
    CardComponent,
    StatCardComponent,
    ChartCardComponent
  ],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css'
})
export class AdminDashboardComponent {}

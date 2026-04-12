import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardComponent } from '../shared/components/card/card.component';
import { StatCardComponent } from '../shared/components/stat-card/stat-card.component';
import { ChartCardComponent } from '../shared/components/chart-card/chart-card.component';
import { PageHeaderComponent, BreadcrumbItem } from '../shared/components/page-header/page-header.component';
import { IconComponent } from '../shared/components/icon/icon.component';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    StatCardComponent,
    ChartCardComponent,
    PageHeaderComponent,
    IconComponent
  ],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css'
})
export class AdminDashboardComponent {
  breadcrumbs: BreadcrumbItem[] = [
    { label: 'Dashboard' }
  ];
}

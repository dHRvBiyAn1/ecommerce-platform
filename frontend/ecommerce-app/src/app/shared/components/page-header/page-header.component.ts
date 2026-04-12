import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';
import { RouterModule } from '@angular/router';

export interface BreadcrumbItem {
  label: string;
  link?: string;
}

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.css'
})
export class PageHeaderComponent {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() breadcrumbs: BreadcrumbItem[] = [];
}

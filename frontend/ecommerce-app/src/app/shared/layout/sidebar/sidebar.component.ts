import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { IconComponent } from '../../components/icon/icon.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css'
})
export class SidebarComponent {
  isDashboardOpen = false;
  constructor(private router: Router) {
    // Auto-expand dashboard dropdown when navigating to a dashboard child route
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const url = event.urlAfterRedirects || event.url;
      if (url.startsWith('/admin')) {
        this.isDashboardOpen = true;
      }
    });
  }

  toggleDashboard(): void {
    this.isDashboardOpen = !this.isDashboardOpen;
  }
}

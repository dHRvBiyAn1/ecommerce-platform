import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { IconComponent } from '../../components/icon/icon.component';
import { ThemeService } from '../../../core/services/theme.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-store-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  templateUrl: './store-navbar.component.html',
  styleUrl: './store-navbar.component.css'
})
export class StoreNavbarComponent {
  theme$: Observable<'dark' | 'light'>;
  isScrolled = false;
  mobileMenuOpen = false;
  cartCount = 3;

  navLinks = [
    { label: 'Home', route: '/' },
    { label: 'Shop', route: '/shop' },
    { label: 'Categories', route: '/shop' },
    { label: 'Deals', route: '/shop' }
  ];

  constructor(private themeService: ThemeService) {
    this.theme$ = this.themeService.theme$;
  }

  @HostListener('window:scroll')
  onScroll() {
    this.isScrolled = window.scrollY > 20;
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }

  toggleMobileMenu() {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }
}

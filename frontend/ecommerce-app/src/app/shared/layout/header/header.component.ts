import { Component, ViewChild, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { ThemeService } from '../../../core/services/theme.service';
import { SidebarService } from '../../../core/services/sidebar.service';
import { IconComponent } from '../../components/icon/icon.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent {
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;
  theme$: Observable<'dark' | 'light'>;

  constructor(
    private themeService: ThemeService,
    private sidebarService: SidebarService
  ) {
    this.theme$ = this.themeService.theme$;
  }

  toggleSidebar() {
    this.sidebarService.toggle();
  }

  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent) {
    // Check for cmd+k (Mac) or ctrl+k (Windows/Linux)
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.searchInput.nativeElement.focus();
    }
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }
}

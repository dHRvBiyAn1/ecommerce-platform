import { Component, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { AppTopbar } from './app-topbar';
import { AppSidebar } from './app-sidebar';
import { AppFooter } from './app-footer';
import { LayoutService } from '../service/layout.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, AppTopbar, AppSidebar, RouterModule, AppFooter, ToastModule],
  template: `
    <p-toast></p-toast>
    <div class="layout-wrapper" [ngClass]="containerClass()">
      <app-topbar></app-topbar>
      <app-sidebar></app-sidebar>
      <div class="layout-main-container">
        <div class="layout-main">
          <router-outlet></router-outlet>
        </div>
        <app-footer></app-footer>
      </div>
      @if (isOverlayActive()) {
        <div class="layout-mask" (click)="layoutService.onMenuToggle()"></div>
      }
    </div>
  `,
})
export class AppLayout {
  layoutService = inject(LayoutService);

  constructor() {
    effect(() => {
      const state = this.layoutService.layoutState();
      document.body.classList.toggle('blocked-scroll', !!state.mobileMenuActive);
    });
  }

  containerClass = computed(() => {
    const config = this.layoutService.layoutConfig();
    const state = this.layoutService.layoutState();
    return {
      'layout-overlay': config.menuMode === 'overlay',
      'layout-static': config.menuMode === 'static',
      'layout-static-inactive': state.staticMenuDesktopInactive && config.menuMode === 'static',
      'layout-overlay-active': state.overlayMenuActive,
      'layout-mobile-active': state.mobileMenuActive,
    };
  });

  isOverlayActive = computed(
    () => this.layoutService.layoutState().overlayMenuActive || this.layoutService.layoutState().mobileMenuActive
  );
}

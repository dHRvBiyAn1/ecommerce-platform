import { Injectable, effect, signal, computed } from '@angular/core';

export interface LayoutConfig {
  preset: string;
  primary: string;
  surface: string | undefined | null;
  darkTheme: boolean;
  menuMode: string;
}

interface LayoutState {
  staticMenuDesktopInactive: boolean;
  overlayMenuActive: boolean;
  configSidebarVisible: boolean;
  mobileMenuActive: boolean;
  menuHoverActive: boolean;
  activePath: string | null;
}

@Injectable({ providedIn: 'root' })
export class LayoutService {
  layoutConfig = signal<LayoutConfig>({
    preset: 'Aura',
    primary: 'emerald',
    surface: null,
    darkTheme: true,
    menuMode: 'overlay',
  });

  layoutState = signal<LayoutState>({
    staticMenuDesktopInactive: false,
    overlayMenuActive: false,
    configSidebarVisible: false,
    mobileMenuActive: false,
    menuHoverActive: false,
    activePath: null,
  });

  isDarkTheme = computed(() => this.layoutConfig().darkTheme);
  isOverlay = computed(() => this.layoutConfig().menuMode === 'overlay');
  isSidebarActive = computed(
    () => this.layoutState().overlayMenuActive || this.layoutState().mobileMenuActive
  );

  private initialized = false;

  constructor() {
    effect(() => {
      const config = this.layoutConfig();
      if (!this.initialized || !config) {
        this.initialized = true;
        return;
      }
      this.handleDarkModeTransition(config);
    });
  }

  private handleDarkModeTransition(config: LayoutConfig): void {
    if ('startViewTransition' in document) {
      document.startViewTransition(() => this.toggleDarkMode(config));
    } else {
      this.toggleDarkMode(config);
    }
  }

  toggleDarkMode(config?: LayoutConfig): void {
    const c = config || this.layoutConfig();
    document.documentElement.classList.toggle('app-dark', c.darkTheme);
  }

  onMenuToggle() {
    if (this.isOverlay()) {
      this.layoutState.update((prev) => ({
        ...prev,
        overlayMenuActive: !prev.overlayMenuActive,
      }));
    }
    if (window.innerWidth > 991) {
      this.layoutState.update((prev) => ({
        ...prev,
        staticMenuDesktopInactive: !prev.staticMenuDesktopInactive,
      }));
    } else {
      this.layoutState.update((prev) => ({
        ...prev,
        mobileMenuActive: !prev.mobileMenuActive,
      }));
    }
  }

  isDesktop(): boolean {
    return window.innerWidth > 991;
  }

  isMobile(): boolean {
    return !this.isDesktop();
  }
}

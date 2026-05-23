import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, computed, inject, PLATFORM_ID } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { $t, updatePreset, updateSurfacePalette } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import Lara from '@primeuix/themes/lara';
import Nora from '@primeuix/themes/nora';
import { SelectButtonModule } from 'primeng/selectbutton';
import { LayoutService } from '../service/layout.service';

const presets: Record<string, any> = { Aura, Lara, Nora };

interface SurfaceOption {
  name: string;
  palette?: Record<string, string>;
}

@Component({
  selector: 'app-configurator',
  standalone: true,
  imports: [CommonModule, FormsModule, SelectButtonModule],
  template: `
    <div class="flex flex-col gap-4">
      <div>
        <span class="text-sm text-muted-color font-semibold">Primary</span>
        <div class="pt-2 flex gap-2 flex-wrap justify-start">
          @for (color of primaryColors(); track color.name) {
            <button
              type="button"
              [title]="color.name"
              (click)="updateColors($event, 'primary', color)"
              [ngClass]="{ 'outline outline-primary': color.name === selectedPrimaryColor() }"
              class="cursor-pointer w-5 h-5 rounded-full flex shrink-0 items-center justify-center outline-offset-1 shadow"
              [style]="{ 'background-color': color.name === 'noir' ? 'var(--text-color)' : color.palette?.['500'] }"
            ></button>
          }
        </div>
      </div>
      <div>
        <span class="text-sm text-muted-color font-semibold">Surface</span>
        <div class="pt-2 flex gap-2 flex-wrap justify-start">
          @for (surface of surfaces; track surface.name) {
            <button
              type="button"
              [title]="surface.name"
              (click)="updateColors($event, 'surface', surface)"
              class="cursor-pointer w-5 h-5 rounded-full flex shrink-0 items-center justify-center p-0 outline-offset-1"
              [ngClass]="{ 'outline outline-primary': surface.name === (selectedSurfaceColor() ?? (layoutService.layoutConfig().darkTheme ? 'zinc' : 'slate')) }"
              [style]="{ 'background-color': surface.palette?.['500'] }"
            ></button>
          }
        </div>
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-sm text-muted-color font-semibold">Presets</span>
        <p-selectbutton
          [options]="presetKeys"
          [ngModel]="selectedPreset()"
          (ngModelChange)="onPresetChange($event)"
          [allowEmpty]="false"
          size="small"
        />
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-sm text-muted-color font-semibold">Menu Mode</span>
        <p-selectbutton
          [ngModel]="menuMode()"
          (ngModelChange)="onMenuModeChange($event)"
          [options]="menuModeOptions"
          [allowEmpty]="false"
          size="small"
        />
      </div>
    </div>
  `,
  host: {
    class:
      'hidden absolute top-13 right-0 w-72 p-4 bg-surface-0 dark:bg-surface-900 border border-surface rounded-border origin-top shadow-[0px_3px_5px_rgba(0,0,0,0.02),0px_0px_2px_rgba(0,0,0,0.05),0px_1px_4px_rgba(0,0,0,0.08)]',
  },
})
export class AppConfigurator {
  layoutService = inject(LayoutService);
  platformId = inject(PLATFORM_ID);

  presetKeys = Object.keys(presets);
  menuModeOptions = [
    { label: 'Static', value: 'static' },
    { label: 'Overlay', value: 'overlay' },
  ];

  surfaces: SurfaceOption[] = [
    { name: 'slate', palette: { 50: '#f8fafc', 100: '#f1f5f9', 200: '#e2e8f0', 300: '#cbd5e1', 400: '#94a3b8', 500: '#64748b', 600: '#475569', 700: '#334155', 800: '#1e293b', 900: '#0f172a', 950: '#020617' } },
    { name: 'gray', palette: { 50: '#f9fafb', 100: '#f3f4f6', 200: '#e5e7eb', 300: '#d1d5db', 400: '#9ca3af', 500: '#6b7280', 600: '#4b5563', 700: '#374151', 800: '#1f2937', 900: '#111827', 950: '#030712' } },
    { name: 'zinc', palette: { 50: '#fafafa', 100: '#f4f4f5', 200: '#e4e4e7', 300: '#d4d4d8', 400: '#a1a1aa', 500: '#71717a', 600: '#52525b', 700: '#3f3f46', 800: '#27272a', 900: '#18181b', 950: '#09090b' } },
    { name: 'neutral', palette: { 50: '#fafafa', 100: '#f5f5f5', 200: '#e5e5e5', 300: '#d4d4d4', 400: '#a3a3a3', 500: '#737373', 600: '#525252', 700: '#404040', 800: '#262626', 900: '#171717', 950: '#0a0a0a' } },
    { name: 'stone', palette: { 50: '#fafaf9', 100: '#f5f5f4', 200: '#e7e5e4', 300: '#d6d3d1', 400: '#a8a29e', 500: '#78716c', 600: '#57534e', 700: '#44403c', 800: '#292524', 900: '#1c1917', 950: '#0c0a09' } },
  ];

  selectedPrimaryColor = computed(() => this.layoutService.layoutConfig().primary);
  selectedSurfaceColor = computed(() => this.layoutService.layoutConfig().surface);
  selectedPreset = computed(() => this.layoutService.layoutConfig().preset);
  menuMode = computed(() => this.layoutService.layoutConfig().menuMode);

  primaryColors = computed<SurfaceOption[]>(() => {
    const presetPalette = presets[this.layoutService.layoutConfig().preset]?.primitive;
    const colors = ['emerald', 'green', 'lime', 'orange', 'amber', 'yellow', 'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose'];
    const result: SurfaceOption[] = [{ name: 'noir', palette: {} }];
    for (const color of colors) {
      result.push({ name: color, palette: presetPalette?.[color] });
    }
    return result;
  });

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.onPresetChange(this.layoutService.layoutConfig().preset);
    }
  }

  private getPresetExt() {
    const color = this.primaryColors().find((c) => c.name === this.selectedPrimaryColor()) || { name: 'noir', palette: {} };
    const preset = this.layoutService.layoutConfig().preset;

    if (color.name === 'noir') {
      return {
        semantic: {
          primary: {
            50: '{surface.50}', 100: '{surface.100}', 200: '{surface.200}',
            300: '{surface.300}', 400: '{surface.400}', 500: '{surface.500}',
            600: '{surface.600}', 700: '{surface.700}', 800: '{surface.800}',
            900: '{surface.900}', 950: '{surface.950}',
          },
          colorScheme: {
            light: {
              primary: { color: '{primary.950}', contrastColor: '#ffffff', hoverColor: '{primary.800}', activeColor: '{primary.700}' },
              highlight: { background: '{primary.950}', focusBackground: '{primary.700}', color: '#ffffff', focusColor: '#ffffff' },
            },
            dark: {
              primary: { color: '{primary.50}', contrastColor: '{primary.950}', hoverColor: '{primary.200}', activeColor: '{primary.300}' },
              highlight: { background: '{primary.50}', focusBackground: '{primary.300}', color: '{primary.950}', focusColor: '{primary.950}' },
            },
          },
        },
      };
    }
    return {
      semantic: {
        primary: color.palette,
        colorScheme: {
          light: {
            primary: { color: '{primary.500}', contrastColor: '#ffffff', hoverColor: '{primary.600}', activeColor: '{primary.700}' },
            highlight: { background: '{primary.50}', focusBackground: '{primary.100}', color: '{primary.700}', focusColor: '{primary.800}' },
          },
          dark: {
            primary: { color: '{primary.400}', contrastColor: '{surface.900}', hoverColor: '{primary.300}', activeColor: '{primary.200}' },
            highlight: { background: 'color-mix(in srgb, {primary.400}, transparent 84%)', focusBackground: 'color-mix(in srgb, {primary.400}, transparent 76%)', color: 'rgba(255,255,255,.87)', focusColor: 'rgba(255,255,255,.87)' },
          },
        },
      },
    };
  }

  updateColors(event: Event, type: string, color: SurfaceOption) {
    if (type === 'primary') {
      this.layoutService.layoutConfig.update((state) => ({ ...state, primary: color.name }));
    } else if (type === 'surface') {
      this.layoutService.layoutConfig.update((state) => ({ ...state, surface: color.name }));
    }
    if (type === 'primary') {
      updatePreset(this.getPresetExt());
    } else if (type === 'surface' && color.palette) {
      updateSurfacePalette(color.palette);
    }
    event.stopPropagation();
  }

  onPresetChange(event: string) {
    this.layoutService.layoutConfig.update((state) => ({ ...state, preset: event }));
    const preset = presets[event];
    const surfacePalette = this.surfaces.find((s) => s.name === this.selectedSurfaceColor())?.palette;
    $t().preset(preset).preset(this.getPresetExt()).surfacePalette(surfacePalette).use({ useDefaultOptions: true });
  }

  onMenuModeChange(event: string) {
    this.layoutService.layoutConfig.update((prev) => ({ ...prev, menuMode: event }));
  }
}

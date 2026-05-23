import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink, ButtonModule],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center bg-surface-50 dark:bg-surface-950 p-4">
      <div class="text-center">
        <div class="text-8xl font-bold text-surface-300 dark:text-surface-700 mb-4">404</div>
        <i class="pi pi-exclamation-circle text-4xl text-primary mb-4 block"></i>
        <h1 class="text-2xl font-bold text-surface-900 dark:text-surface-0 mb-2">Page Not Found</h1>
        <p class="text-surface-500 mb-6">The page you're looking for doesn't exist or has been moved.</p>
        <a pButton routerLink="/" label="Go to Home"></a>
      </div>
    </div>
  `,
})
export class NotFound {}

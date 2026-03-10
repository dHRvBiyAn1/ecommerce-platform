import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-seller-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-page">
      <div class="dashboard-card">
        <div class="header">
          <div class="icon-wrapper">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path><line x1="3" y1="6" x2="21" y2="6"></line><path d="M16 10a4 4 0 0 1-8 0"></path></svg>
          </div>
          <h1>Seller Dashboard</h1>
          <p>Manage your inventory and track your ShopVerse sales.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-page {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 24px;
    }
    .dashboard-card {
      width: 100%;
      max-width: 600px;
      background: var(--glass-bg, rgba(255, 255, 255, 0.05));
      backdrop-filter: blur(var(--glass-blur, 20px));
      border: 1px solid var(--glass-border, rgba(255, 255, 255, 0.1));
      border-radius: var(--radius-xl, 24px);
      padding: 40px;
      text-align: center;
    }
    .icon-wrapper {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      background: linear-gradient(135deg, var(--color-success, #34d399), var(--color-primary, #6366f1));
      border-radius: 16px;
      margin-bottom: 24px;
      color: white;
    }
    .icon-wrapper svg {
      width: 32px;
      height: 32px;
    }
    h1 {
      font-size: 2rem;
      margin-bottom: 8px;
    }
    p {
      color: var(--color-text-muted, #94a3b8);
    }
  `]
})
export class SellerDashboardComponent {}

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button 
      [type]="type" 
      class="btn" 
      [ngClass]="'btn-' + variant" 
      [disabled]="disabled || loading"
    >
      @if (loading) {
        <span class="spinner"></span>
        <span>{{ loadingText || text }}</span>
      } @else {
        <span>{{ text }}</span>
        <ng-content select="[icon]"></ng-content>
      }
    </button>
  `,
  styles: [`
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 13px 24px;
      border: none;
      border-radius: var(--radius-md, 12px);
      font-family: inherit;
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
      transition: all var(--transition-base, 250ms);
      position: relative;
      overflow: hidden;
      width: 100%;
    }
    .btn-primary {
      background: linear-gradient(135deg, var(--color-primary, #6366f1), var(--color-primary-dark, #4f46e5));
      color: white;
      box-shadow: 0 4px 16px rgba(99, 102, 241, 0.25);
    }
    .btn-primary:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 6px 24px rgba(99, 102, 241, 0.35);
    }
    .btn-primary:active:not(:disabled) {
      transform: translateY(0);
    }
    .btn:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }
    ::ng-deep .btn-icon {
      width: 16px;
      height: 16px;
      transition: transform var(--transition-fast, 150ms);
    }
    .btn:hover:not(:disabled) ::ng-deep .btn-icon {
      transform: translateX(3px);
    }
    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class ButtonComponent {
  @Input() text = '';
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() variant: 'primary' | 'secondary' | 'danger' = 'primary';
  @Input() disabled = false;
  @Input() loading = false;
  @Input() loadingText = '';
}

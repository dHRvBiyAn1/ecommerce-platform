import { Component, Input, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => InputComponent),
      multi: true
    }
  ],
  template: `
    <div class="form-group" [class.has-error]="hasError">
      <label [for]="id">{{ label }}</label>
      <div class="input-wrapper">
        <div class="icon-container">
          <ng-content select="[icon]"></ng-content>
        </div>
        <input
          [id]="id"
          [type]="type"
          [placeholder]="placeholder"
          [attr.autocomplete]="autocomplete"
          [value]="value"
          (input)="onInput($event)"
          (blur)="onTouched()"
        />
      </div>
      @if (hasError && errorMessage) {
        <div class="field-error">
          {{ errorMessage }}
        </div>
      }
    </div>
  `,
  styles: [`
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .form-group label {
      font-size: 0.825rem;
      font-weight: 500;
      color: var(--color-text-muted, #94a3b8);
      letter-spacing: 0.01em;
    }
    .input-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }
    .icon-container {
      position: absolute;
      left: 14px;
      width: 18px;
      height: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-text-dimmed, #64748b);
      pointer-events: none;
      transition: color var(--transition-fast, 150ms);
    }
    ::ng-deep .icon-container svg {
      width: 18px;
      height: 18px;
    }
    .input-wrapper input {
      width: 100%;
      padding: 12px 16px 12px 44px;
      background: var(--color-surface, rgba(255, 255, 255, 0.04));
      border: 1px solid var(--color-border, rgba(255, 255, 255, 0.08));
      border-radius: var(--radius-md, 12px);
      color: var(--color-text, #f1f5f9);
      font-family: inherit;
      font-size: 0.925rem;
      transition: all var(--transition-fast, 150ms);
    }
    .input-wrapper input::placeholder {
      color: var(--color-text-dimmed, #64748b);
    }
    .input-wrapper input:focus {
      outline: none;
      border-color: var(--color-border-focus, rgba(99, 102, 241, 0.5));
      background: var(--color-surface-hover, rgba(255, 255, 255, 0.07));
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }
    .input-wrapper input:focus ~ .icon-container,
    .input-wrapper:focus-within .icon-container {
      color: var(--color-primary-light, #818cf8);
    }
    .form-group.has-error .input-wrapper input {
      border-color: rgba(248, 113, 113, 0.4);
    }
    .field-error {
      font-size: 0.8rem;
      color: var(--color-error, #f87171);
      padding-left: 2px;
      animation: fadeSlideIn 0.2s ease;
    }
    @keyframes fadeSlideIn {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class InputComponent implements ControlValueAccessor {
  @Input() id = '';
  @Input() label = '';
  @Input() type = 'text';
  @Input() placeholder = '';
  @Input() autocomplete = 'off';
  @Input() hasError = false;
  @Input() errorMessage = '';

  value: string = '';

  onChange = (val: string) => {};
  onTouched = () => {};

  writeValue(val: string): void {
    this.value = val;
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  onInput(event: Event) {
    const target = event.target as HTMLInputElement;
    this.value = target.value;
    this.onChange(this.value);
  }
}

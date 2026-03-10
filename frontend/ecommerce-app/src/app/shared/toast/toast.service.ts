import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'info';
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private toastsSubject = new BehaviorSubject<Toast[]>([]);
  public toasts$ = this.toastsSubject.asObservable();

  show(type: 'success' | 'error' | 'info', message: string, duration = 4000) {
    const id = Math.random().toString(36).substring(2, 9);
    const toast: Toast = { id, type, message };
    const currentToasts = this.toastsSubject.value;
    
    // Auto-remove previous toasts to keep it clean, or allow stacking. Let's stack up to 3.
    const newToasts = [...currentToasts, toast].slice(-3);
    this.toastsSubject.next(newToasts);

    setTimeout(() => {
      this.remove(id);
    }, duration);
  }

  success(message: string, duration = 4000) {
    this.show('success', message, duration);
  }

  error(message: string, duration = 4000) {
    this.show('error', message, duration);
  }

  info(message: string, duration = 4000) {
    this.show('info', message, duration);
  }

  remove(id: string) {
    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next(currentToasts.filter(t => t.id !== id));
  }
}

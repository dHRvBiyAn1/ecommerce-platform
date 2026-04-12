import { Component, ChangeDetectorRef, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from './toast.service';
import { Subscription } from 'rxjs';
import { IconComponent } from '../components/icon/icon.component';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush   // ← ADD THIS
})
export class ToastComponent implements OnInit, OnDestroy {
  toasts: Toast[] = [];
  private subscription: Subscription = new Subscription;

  constructor(
    private toastService: ToastService,
    private cdr: ChangeDetectorRef               // ← ADD THIS
  ) {}

  ngOnInit() {
    // ← Move subscription OUT of constructor into ngOnInit
    this.subscription = this.toastService.toasts$.subscribe(t => {
      this.toasts = t;
      this.cdr.markForCheck();                   // ← trigger change detection safely
    });
  }

  ngOnDestroy() {
    this.subscription?.unsubscribe();            // ← prevent memory leak
  }

  remove(id: string) {
    this.toastService.remove(id);
  }
}

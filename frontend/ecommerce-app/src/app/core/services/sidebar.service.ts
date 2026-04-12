import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SidebarService {
  private isCollapsedSubject = new BehaviorSubject<boolean>(false);
  isCollapsed$ = this.isCollapsedSubject.asObservable();

  toggle(): void {
    this.isCollapsedSubject.next(!this.isCollapsedSubject.value);
  }

  setCollapsed(value: boolean): void {
    this.isCollapsedSubject.next(value);
  }

  get isCollapsed(): boolean {
    return this.isCollapsedSubject.value;
  }
}

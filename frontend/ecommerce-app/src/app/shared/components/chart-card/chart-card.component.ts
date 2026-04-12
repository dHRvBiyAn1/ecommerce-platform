import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-chart-card',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './chart-card.component.html',
  styleUrl: './chart-card.component.css'
})
export class ChartCardComponent {
  @Input() title = '';
  @Input() subtitle?: string;
  @Input() type: 'bar' | 'area' | 'radial' = 'bar';
}

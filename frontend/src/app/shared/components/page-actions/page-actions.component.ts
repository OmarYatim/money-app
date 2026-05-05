import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-page-actions',
  imports: [MatIconModule],
  templateUrl: './page-actions.component.html',
  styleUrl: './page-actions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageActionsComponent {
  protected readonly todayLabel = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());
}

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';

@Component({
  selector: 'app-goals',
  imports: [RouterLink, PageActionsComponent],
  templateUrl: './goals.component.html',
  styleUrl: './goals.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsComponent {}

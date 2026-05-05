import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';

@Component({
  selector: 'app-reports',
  imports: [RouterLink, PageActionsComponent],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsComponent {}

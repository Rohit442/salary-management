# ACME Salary Management — Figma UI

[Open the editable Figma Design file](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk)

## Main screens

| Screen | Design coverage |
|---|---|
| [01 Sign in](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-64) | Provisioned HR account sign-in |
| [02 Employee directory](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-65) | Search, filters, pagination, and employee navigation |
| [03 Employee details](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-66) | Employment dates, annual/monthly base salary, salary effective date, and selected-month estimate |
| [04 Salary history](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-67) | Initial salary baseline and changes with separate effective and recording dates |
| [05 Update salary](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-68) | Salary, required reason/effective date, whole-month pay review, and employment-date context |
| [06 Add employee](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-69) | Employee details, joining date, and initial salary effective date |
| [07 Edit employee](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-70) | Joining and optional last employment date, with read-only salary baseline/current rate |
| [08 Compensation reports](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=2-71) | Current active-employee annual compensation metrics by currency |

The file uses editable text, auto layout, semantic color and spacing variables, and reusable ACME buttons, inputs, badges, and alerts. The visual system uses navy navigation, blue actions, white surfaces, and Roboto. The eight main screens are arranged in two columns to the left of the component kit and feedback board, with supporting screen variants below them.

## Monthly estimate and salary dates

The monthly estimate stays within Employee details. Show the selected month, applicable annual salary and effective date, eligible/calendar days, formula, amount/currency, and generated time. The [monthly API contract](../api-base-pay.md) defines the calculation; [salary history](../api-salaries.md) defines effective dates and the immutable initial baseline.

The reference example uses INR 1,200,000 annual salary from April 1, 2026, a raise to INR 1,800,000 effective October 15, and a last employment date of November 12. September uses INR 100,000 monthly base pay; all October uses INR 150,000; November uses INR 60,000 for 12 of 30 calendar days.

Joining and last employment dates are inclusive. Future end dates remain separate from inactive status. Inactive records retain salary-history and estimate access. Display the initial salary above subsequent changes; distinguish the salary effective date from the recording timestamp.

## Supporting states

- [Salary saved](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-75), including updated salary and monthly estimate.
- [History after salary update](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-175), with the initial baseline and effective/recorded columns.
- [Employee inactive](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-285), with preserved history and monthly estimates.
- [Employee created](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-394) and [new employee salary history](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=7-415).
- [Failed salary save](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-500) and [conflicting salary edit](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-584), preserving amount, reason, and effective date.
- [Mark inactive confirmation](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-668), separating status from employment dates.
- [Feedback and recovery board](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=6-677), including estimate loading, no employment, error/retry, incomplete salary history, and date-validation examples, alongside existing access, form, directory, report, and status states.

## Prototype and handoff

Core navigation connects the directory, employee record, salary update, history, add/edit forms, inactive confirmation, and saved/recovery variants. Entry points cover sign-in, reports, failed saves, and conflicts. Monthly selector and estimate states are editable design samples; their request/response behavior is specified in the API contract.

## Review

The main employment/salary screens and supporting salary/history/recovery variants were reviewed in the browser. Repairs included frame overlap and height, estimate-control wrapping, result-card widths, salary-history columns and text wrapping, the missing conflict effective-date field, inactive history access, and uncertain-save wording. Existing directory/dropdown refinements were retained.

The updated Employee details screen was reloaded to confirm persistence. Saved review images cover [monthly estimates](review/monthly-base-pay.png), [salary updates](review/salary-update.png), [salary history](review/salary-history.png), [employee creation](review/employee-add.png), [employee editing](review/employee-edit.png), and the [inactive confirmation](review/inactive-confirmation.png).

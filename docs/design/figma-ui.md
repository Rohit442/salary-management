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

## Directory revision

The requested directory revision retains the existing visual system and employee table. The Figma directory and [name suggestions preview](https://www.figma.com/design/BWWGmSVxW6LTlJu6yVr3Tk?node-id=47-259) have been updated and visually reviewed in the browser; the [saved preview](review/directory-suggestions.png) shows names only, fixed 100-row pages and direct page entry. The frontend and separate suggestions endpoint implement this revision, verified with fixture-backed and real-backend Playwright checks.

- **Existing table search:** retain the current name/employee-ID substring matching and API regression expectations. Typing for suggestions does not submit or filter the table; the UI applies the table search on blur or Enter.
- **Employee suggestions:** show matching names below the existing search box after at least one character and a 300 ms typing pause. Respect the applied Country, Department, Job level and Currency filters, combining values within each category with OR and categories with AND. Match the beginning of the full name, ignoring case: `R` suggests names beginning with R and `Ra` narrows those suggestions. The dropdown displays names only, without IDs, departments, job titles or helper text; selecting a name opens that employee’s profile. Use no A–Z, letter-filter or match-mode control.
- **Suggestion interaction:** Arrow keys move through suggestions; Enter opens the highlighted result. With no highlighted suggestion, Enter applies the table search. Escape or an outside click dismisses the list; clearing the input removes it. Discard stale responses, and keep table search/pagination available if suggestions fail.
- **Automatic filtering:** apply a changed table filter when focus leaves its entire control, including any dropdown popup. Moving focus within the control or merely closing its popup does not apply it. Table search applies on blur or Enter; the suggestion list alone updates while typing. Remove the directory’s **Apply filters** button. Applying changed filters or search resets to page 1.
- **Clear filters:** place beside the filter dropdowns and apply immediately. Clear search, suggestions and all filter categories while retaining sort; reload page 1.
- **Pagination:** use a fixed 100 rows per page, with no size selector; the frontend explicitly sends `size=100`. Retain Previous/Next and add **Page [number] of [total]** with **Go**; Enter submits the same action. Accept only whole numbers from 1 to the current total pages. Invalid input leaves the current page unchanged with a validation message; omit the jump control when there are no results. A 10,000-employee result has 100 pages. Explicit sort changes reset to page 1; paging and sorting use their explicit actions rather than filter-blur behavior.
- **Return from an employee:** restore search text, the applied table query, filters, sort, page number and scroll position through the directory link or browser Back, including after selecting a suggestion. Refresh records while retaining that context; clamp a now-out-of-range page to the last available page. With no remaining matches, show the empty state at page 1.

The [navigation flow](../ui-flows/01-navigation.mmd) reflects the adopted behavior. The existing [employee list API](../api-employees.md#5-get-apiemployees), including `q` substring matching, default page size 25 and allowed size range 1–100, stays unchanged; the frontend’s explicit `size=100` is already supported. A separate authenticated `GET /api/employees/suggestions?prefix=R&limit=8` is implemented: trim `prefix` and require 1–100 characters; default `limit` to 8 and allow integers from 1 to 10. Match a case-insensitive literal full-name prefix, sort deterministically by lowercase name and internal employee ID, and return only `employeeId` and `fullName`; keep the ID internal for profile navigation. The [suggestions contract](../api-employees.md#51-get-apiemployeessuggestions) defines the request and response.

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

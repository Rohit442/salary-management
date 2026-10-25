import { formatDateTime } from '../util/format.mjs';
import { validateSalaryAmount, validateSalaryEffectiveDate } from '../validator/salary.mjs';

const employeePath = id => `/employees/${encodeURIComponent(id)}`;
const today = () => new Date().toISOString().slice(0, 10);
const detailFields = ['fullName', 'workEmail', 'countryCode', 'department', 'jobTitle', 'jobLevel', 'joiningDate'];

export function buildEmployeePayload(draft, existing) {
  const body = Object.fromEntries(detailFields.map(key => [key, draft[key]]));
  if (existing) return { ...body, lastEmploymentDate: draft.lastEmploymentDate || null, version: existing.version };
  return { ...body, employeeId: draft.employeeId, annualBaseSalary: draft.annualBaseSalary,
    currency: draft.currency, initialSalaryEffectiveFrom: draft.initialSalaryEffectiveFrom };
}

export function buildSalaryPayload(draft, employee) {
  return { annualBaseSalary: draft.annualBaseSalary, effectiveDate: draft.effectiveDate,
    reason: draft.reason, version: employee.version };
}

function setup(vm, $scope) {
  let destroyed = false;
  $scope.$on('$destroy', () => { destroyed = true; });
  vm.paint = () => { if (!destroyed) $scope.$applyAsync(); };
  vm.fieldError = field => (vm.error?.fieldErrors || []).filter(item => item.field === field).map(item => item.message).join(' ');
  vm.initials = name => (name || '').split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('');
  vm.label = (items, code) => (items || []).find(item => (item.code || item.value) === code)?.label || code;
  vm.dateTime = formatDateTime;
  vm.fail = error => { vm.error = error; vm.paint(); focusError(); };
  return () => !destroyed;
}

function focusError() {
  setTimeout(() => document.querySelector('.employee-page [role="alert"]')?.focus(), 0);
}

const loadState = `
  <div ng-if="$ctrl.loading" class="card employee-loading" role="status">Loading employee information…</div>
  <div ng-if="$ctrl.loadError" class="alert alert-error" role="alert" tabindex="-1">
    <strong>We couldn’t load this page.</strong><p>{{$ctrl.loadError.message}}</p>
    <p class="request-id" ng-if="$ctrl.loadError.requestId">Reference: {{$ctrl.loadError.requestId}}</p>
    <button class="btn btn-secondary" ng-click="$ctrl.load()">Try again</button>
  </div>`;

const errorState = `
  <div ng-if="$ctrl.error" class="alert alert-error" role="alert" tabindex="-1">
    <strong>{{$ctrl.uncertain ? 'The save outcome is not confirmed.' : 'Please review before continuing.'}}</strong>
    <p>{{$ctrl.error.message}}</p>
    <ul ng-if="$ctrl.error.fieldErrors.length"><li ng-repeat="issue in $ctrl.error.fieldErrors">{{issue.message}}</li></ul>
    <p class="request-id" ng-if="$ctrl.error.requestId">Reference: {{$ctrl.error.requestId}}</p>
  </div>`;

const detailTemplate = `
<section class="employee-page">
  <div class="page-header"><div><h1 class="page-title">Employee details</h1><p class="muted"><a href="#!/employees">Employee directory</a> / {{$ctrl.id}}</p></div>
    <div class="page-actions" ng-if="$ctrl.employee"><a class="btn btn-secondary" ng-href="#!/employees/{{$ctrl.id}}/edit">Edit details</a><a class="btn btn-primary" ng-if="$ctrl.employee.employmentStatus === 'ACTIVE'" ng-href="#!/employees/{{$ctrl.id}}/salary">Update salary</a></div>
  </div>
  ${loadState}
  <div ng-if="$ctrl.employee && !$ctrl.loading && !$ctrl.loadError">
    <div ng-if="$ctrl.saved" class="alert alert-success" role="status">{{$ctrl.saved}}</div>
    <div class="card employee-identity"><div class="employee-avatar">{{$ctrl.initials($ctrl.employee.fullName)}}</div><div class="employee-identity-name"><h2>{{$ctrl.employee.fullName}}</h2><p>{{$ctrl.employee.jobTitle}} · {{$ctrl.employee.department}}</p></div><span class="badge" ng-class="{'badge-inactive': $ctrl.employee.employmentStatus === 'INACTIVE'}">{{$ctrl.employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'}}</span><span class="muted employee-id">{{$ctrl.employee.employeeId}}</span></div>
    <div class="employee-columns">
      <section class="card employment-card"><h2 class="section-title">Employment details</h2><dl class="details-grid">
        <div><dt>Work email</dt><dd>{{$ctrl.employee.workEmail}}</dd></div><div><dt>Country</dt><dd>{{$ctrl.label($ctrl.refs.countries, $ctrl.employee.countryCode)}}</dd></div>
        <div><dt>Department</dt><dd>{{$ctrl.label($ctrl.refs.departments, $ctrl.employee.department)}}</dd></div><div><dt>Job title</dt><dd>{{$ctrl.employee.jobTitle}}</dd></div>
        <div><dt>Job level</dt><dd>{{$ctrl.employee.jobLevel}}</dd></div><div><dt>Employment status</dt><dd>{{$ctrl.employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'}}</dd></div>
      </dl></section>
      <section class="card salary-card"><p class="eyebrow">Annual base salary</p><p class="salary-value">{{$ctrl.employee.annualBaseSalary | formatMoney:$ctrl.employee.currency}}</p><p class="muted">Salary effective from {{$ctrl.employee.salaryEffectiveFrom | formatDate}}</p><a class="btn btn-secondary full-width" ng-href="#!/employees/{{$ctrl.id}}/history">View salary history <span aria-hidden="true">→</span></a></section>
    </div>
    <section class="employee-status"><div><h2>Employee status</h2><p class="muted">Joined {{$ctrl.employee.joiningDate | formatDate}}<span ng-if="$ctrl.employee.lastEmploymentDate"> · Last employment date {{$ctrl.employee.lastEmploymentDate | formatDate}} (inclusive)</span></p><p class="muted" ng-if="$ctrl.employee.employmentStatus === 'INACTIVE'">Salary history and monthly estimates remain available.</p></div><button class="btn btn-secondary" ng-if="$ctrl.employee.employmentStatus === 'ACTIVE'" ng-click="$ctrl.confirmInactive($event)" ng-disabled="$ctrl.saving || $ctrl.confirming || $ctrl.statusNeedsReview">Mark inactive</button></section>
    ${errorState}
    <div class="alert alert-info" ng-if="$ctrl.statusNeedsReview"><p>Review the current employee record before another status change.</p><button class="btn btn-secondary" ng-click="$ctrl.load()">Reload employee</button></div>
    <section class="card estimate-card"><div class="estimate-heading"><div><h2 class="section-title">Monthly base-pay estimate</h2><p class="muted">Mid-month salary changes apply to the whole month. Joining and leaving dates determine eligible days.</p></div><span class="badge" ng-if="$ctrl.estimate">Estimate</span></div>
      <form class="estimate-controls" name="estimateForm" ng-submit="$ctrl.calculate(estimateForm)" novalidate>
        <div class="field"><label for="estimate-month">Selected month</label><input id="estimate-month" name="month" type="text" inputmode="numeric" placeholder="YYYY-MM" ng-model="$ctrl.month" ng-change="$ctrl.clearEstimate()" pattern="[0-9]{4}-(0[1-9]|1[0-2])" required aria-describedby="month-hint"><span id="month-hint" class="field-hint">Year and month, for example 2026-11</span></div>
        <button type="submit" class="btn btn-primary" ng-disabled="$ctrl.estimateLoading">{{$ctrl.estimateLoading ? 'Calculating…' : 'Calculate estimate'}}</button>
      </form>
      <div ng-if="$ctrl.estimateError" class="alert alert-error" role="alert"><p>{{$ctrl.estimateError.message}}</p><button class="btn btn-secondary" ng-click="$ctrl.calculate(estimateForm)" ng-disabled="$ctrl.estimateLoading">Try again</button></div>
      <div ng-if="$ctrl.estimateLoading" role="status" class="muted">Calculating the selected month…</div>
      <div ng-if="$ctrl.estimate" class="estimate-results">
        <div class="estimate-summary"><p class="eyebrow">Estimate summary · {{$ctrl.estimate.month}}</p><p class="salary-value">{{$ctrl.estimate.estimatedBasePay | formatMoney:$ctrl.estimate.currency}}</p><p class="muted">{{$ctrl.label($ctrl.refs.currencies, $ctrl.estimate.currency)}}</p><hr><p class="muted">Generated</p><p>{{$ctrl.dateTime($ctrl.estimate.generatedAt)}}</p><span class="badge" ng-if="$ctrl.estimate.isFutureMonth">Future-month projection</span></div>
        <div class="estimate-breakdown"><p class="eyebrow">Estimate breakdown</p><dl>
          <div><dt>Eligible / calendar days</dt><dd>{{$ctrl.estimate.eligibleDays}} / {{$ctrl.estimate.daysInMonth}}</dd></div>
          <div ng-if="$ctrl.estimate.salaryBasis"><dt>Applicable annual salary</dt><dd>{{$ctrl.estimate.salaryBasis.annualBaseSalary | formatMoney:$ctrl.estimate.currency}}</dd></div>
          <div ng-if="$ctrl.estimate.salaryBasis"><dt>Salary effective from</dt><dd>{{$ctrl.estimate.salaryBasis.effectiveDate | formatDate}}</dd></div>
          <div ng-if="$ctrl.estimate.salaryBasis"><dt>Monthly base pay</dt><dd>{{$ctrl.estimate.salaryBasis.monthlyBasePay | formatMoney:$ctrl.estimate.currency}}</dd></div>
          <div><dt>Formula</dt><dd>Annual salary × eligible days ÷ (12 × calendar days)</dd></div>
        </dl><p ng-if="$ctrl.estimate.eligibleDays === 0" class="muted">No employment days fall within this month.</p></div>
      </div>
      <div class="alert alert-info estimate-note"><strong>Estimation note</strong><p>Estimates use recorded salary and employment dates. Only joining or leaving prorates the amount. Inactive employees retain access to their estimates.</p></div>
    </section>
  </div>
</section>`;

function DetailController(api, $routeParams, $location, $scope, $mdDialog) {
  const vm = this;
  const alive = setup(vm, $scope);
  let estimateRequest = 0;
  vm.id = $routeParams.id || $routeParams.employeeId;
  vm.month = today().slice(0, 7);
  vm.refs = {};
  vm.saved = { salary: 'Salary change saved. This record shows the latest employee information.', created: 'Employee created successfully.', details: 'Employee details saved.' }[$location.search().saved];
  vm.load = async () => {
    vm.loading = true; vm.loadError = null; vm.error = null; vm.statusNeedsReview = false;
    try { const employee = await api.get(employeePath(vm.id)); if (alive()) vm.employee = employee; }
    catch (error) { vm.loadError = error; }
    finally { vm.loading = false; vm.paint(); }
    try { vm.refs = await api.references(); vm.paint(); } catch { /* Record codes remain valid labels if reference lookup is unavailable. */ }
  };
  vm.clearEstimate = () => { estimateRequest++; vm.estimate = null; vm.estimateError = null; vm.estimateLoading = false; };
  vm.calculate = async form => {
    if (form?.$invalid || !/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(vm.month || '')) { vm.estimateError = { message: 'Choose a valid month in YYYY-MM format.' }; return; }
    const serial = ++estimateRequest; const month = vm.month;
    vm.estimateLoading = true; vm.estimate = null; vm.estimateError = null;
    try { const result = await api.get(`${employeePath(vm.id)}/base-pay-estimate`, { month }); if (serial === estimateRequest && alive()) vm.estimate = result; }
    catch (error) { if (serial === estimateRequest) vm.estimateError = error; }
    finally { if (serial === estimateRequest) vm.estimateLoading = false; vm.paint(); }
  };
  vm.confirmInactive = async event => {
    if (vm.saving || vm.confirming || vm.statusNeedsReview || vm.employee.employmentStatus !== 'ACTIVE') return;
    vm.confirming = true;
    const reviewed = { ...vm.employee };
    try {
      await $mdDialog.show($mdDialog.confirm().title(`Mark ${reviewed.fullName} inactive?`)
        .textContent(`${reviewed.employeeId} · ${reviewed.department}. This employee will be excluded from compensation reports and salary updates will be disabled. Their record, salary history and monthly estimates remain available. Employment dates stay unchanged.`)
        .ariaLabel('Confirm employee inactivation').targetEvent(event).ok('Mark inactive').cancel('Cancel'));
    } catch { vm.confirming = false; vm.paint(); return; }
    vm.confirming = false; vm.saving = true; vm.error = null; vm.paint();
    try { vm.employee = await api.send('PUT', `${employeePath(vm.id)}/status`, { employmentStatus: 'INACTIVE', version: reviewed.version }); vm.saved = 'Employee marked inactive.'; }
    catch (error) { vm.statusNeedsReview = error.code === 'VERSION_CONFLICT' || error.uncertain; vm.fail(error); }
    finally { vm.saving = false; vm.paint(); }
  };
  vm.$onInit = vm.load;
}

const historyTemplate = `
<section class="employee-page">
  <div class="page-header"><div><h1 class="page-title">Salary change history</h1><p class="muted">{{$ctrl.employee.fullName}} / {{$ctrl.id}}<span ng-if="$ctrl.employee.jobTitle"> / {{$ctrl.employee.jobTitle}}</span></p></div><a class="btn btn-secondary" ng-href="#!/employees/{{$ctrl.id}}">← Employee details</a></div>
  ${loadState}
  <div ng-if="$ctrl.history && !$ctrl.loading && !$ctrl.loadError">
    <section class="card history-overview"><div class="history-summary"><div><p class="eyebrow">Current annual base salary</p><p class="salary-value">{{$ctrl.employee.annualBaseSalary | formatMoney:$ctrl.employee.currency}}</p></div><p class="muted">{{$ctrl.history.totalElements}} recorded {{$ctrl.history.totalElements === 1 ? 'change' : 'changes'}}<br>Initial salary shown above changes</p></div>
      <div class="history-baseline"><div><p class="eyebrow">Initial salary</p><strong>{{$ctrl.history.initialSalary.annualBaseSalary | formatMoney:$ctrl.history.initialSalary.currency}} / year</strong><p class="muted">Effective from {{$ctrl.history.initialSalary.effectiveDate | formatDate}}</p></div><div><p>Recorded {{$ctrl.dateTime($ctrl.history.initialSalary.recordedAt)}}</p><p class="muted">Actor: {{$ctrl.history.initialSalary.actor.displayName}}</p></div></div>
    </section>
    <div class="card table-wrap history-table"><table class="data-table"><caption class="visually-hidden">Recorded salary changes, newest recording first</caption><thead><tr><th scope="col">Effective from</th><th scope="col">Recorded at</th><th scope="col">Previous amount</th><th scope="col">New amount</th><th scope="col">Reason</th><th scope="col">Changed by</th></tr></thead><tbody><tr ng-repeat="change in $ctrl.history.items track by change.changeId"><td>{{change.effectiveDate | formatDate}}</td><td>{{$ctrl.dateTime(change.changedAt)}}</td><td class="money-cell">{{change.previousAmount | formatMoney:change.currency}}</td><td class="money-cell">{{change.newAmount | formatMoney:change.currency}}</td><td class="reason-cell">{{change.reason}}</td><td>{{change.actor.displayName}}</td></tr><tr ng-if="!$ctrl.history.items.length"><td colspan="6" class="empty-row">No salary changes recorded. The initial salary is preserved above.</td></tr></tbody></table></div>
    <div class="employee-pagination" ng-if="$ctrl.history.totalPages > 1"><p class="muted">Page {{$ctrl.history.page + 1}} of {{$ctrl.history.totalPages}}</p><div><button class="btn btn-secondary" ng-disabled="!$ctrl.history.hasPrevious" ng-click="$ctrl.changePage(-1)">Previous</button><button class="btn btn-secondary" ng-disabled="!$ctrl.history.hasNext" ng-click="$ctrl.changePage(1)">Next</button></div></div>
    <div class="alert alert-info"><strong>Effective-dated salary history</strong><p>Effective from and Recorded at are shown separately. A salary change applies to its whole effective month. The initial salary and all recorded changes remain available.</p></div>
  </div>
</section>`;

function HistoryController(api, $routeParams, $scope) {
  const vm = this; const alive = setup(vm, $scope); let sequence = 0;
  vm.id = $routeParams.id || $routeParams.employeeId; vm.page = 0;
  vm.load = async () => {
    const serial = ++sequence; vm.loading = true; vm.loadError = null;
    try {
      const [employee, history] = await Promise.all([api.get(employeePath(vm.id)), api.get(`${employeePath(vm.id)}/salary-changes`, { page: vm.page, size: 25 })]);
      if (serial === sequence && alive()) { vm.employee = employee; vm.history = history; }
    } catch (error) { if (serial === sequence) vm.loadError = error; }
    finally { if (serial === sequence) vm.loading = false; vm.paint(); }
  };
  vm.changePage = direction => { vm.page += direction; vm.load(); };
  vm.$onInit = vm.load;
}

const salaryTemplate = `
<section class="employee-page">
  <div class="page-header"><div><h1 class="page-title">Update salary</h1><p class="muted">{{$ctrl.employee.fullName}} / {{$ctrl.id}}<span ng-if="$ctrl.employee"> / {{$ctrl.employee.department}} · {{$ctrl.employee.jobLevel}}</span></p></div></div>
  ${loadState}
  <div ng-if="$ctrl.employee && !$ctrl.loading && !$ctrl.loadError" class="salary-editor-layout">
    <form class="card salary-editor" name="salaryForm" ng-submit="$ctrl.save(salaryForm)" novalidate>
      <h2 class="section-title">New compensation</h2><p class="muted">Enter the new annual base salary and explain the change.</p>
      <div ng-if="$ctrl.employee.employmentStatus === 'INACTIVE'" class="alert alert-info">Salary updates are unavailable for inactive employees. <a ng-href="#!/employees/{{$ctrl.id}}/history">View preserved salary history</a>.</div>
      ${errorState}
      <div ng-if="$ctrl.uncertain" class="alert alert-info" role="status"><p>Your original change is retained. Retry that same change to confirm whether it was saved.</p><div class="page-actions"><button type="button" class="btn btn-primary" ng-click="$ctrl.retry()" ng-disabled="$ctrl.saving || $ctrl.retryWait || $ctrl.attempts >= 3">{{$ctrl.retryWait ? 'Please wait before retrying' : 'Retry original change'}}</button><button type="button" class="btn btn-secondary" ng-click="$ctrl.review()" ng-disabled="$ctrl.reviewLoading">Review current record</button><button type="button" class="btn btn-secondary" ng-click="$ctrl.reviewHistory()" ng-disabled="$ctrl.historyLoading">View history</button></div><p ng-if="$ctrl.attempts >= 3" class="muted">Three attempts have been made. Review the current record and history before further action.</p></div>
      <section class="review-panel salary-history-review" ng-if="$ctrl.historyReviewOpen" aria-label="Salary history review">
        <h3>Recorded salary history</h3><p class="muted">Review these records alongside your draft. The original change remains available to retry on this page.</p>
        <p ng-if="$ctrl.historyLoading" role="status">Loading salary history…</p>
        <div ng-if="$ctrl.historyError" class="alert alert-error" role="alert"><p>{{$ctrl.historyError.message}}</p><button type="button" class="btn btn-secondary" ng-click="$ctrl.reviewHistory($ctrl.historyPage)">Try history again</button></div>
        <div ng-if="$ctrl.historyReview && !$ctrl.historyLoading && !$ctrl.historyError">
          <div class="history-baseline"><div><p class="eyebrow">Initial salary</p><strong>{{$ctrl.historyReview.initialSalary.annualBaseSalary | formatMoney:$ctrl.historyReview.initialSalary.currency}} / year</strong><p class="muted">Effective from {{$ctrl.historyReview.initialSalary.effectiveDate | formatDate}}</p></div><div><p>Recorded {{$ctrl.dateTime($ctrl.historyReview.initialSalary.recordedAt)}}</p><p class="muted">Actor: {{$ctrl.historyReview.initialSalary.actor.displayName}}</p></div></div>
          <div class="table-wrap history-table"><table class="data-table"><caption class="visually-hidden">Recorded salary changes, newest recording first</caption><thead><tr><th scope="col">Effective from</th><th scope="col">Recorded at</th><th scope="col">Previous amount</th><th scope="col">New amount</th><th scope="col">Reason</th><th scope="col">Changed by</th></tr></thead><tbody><tr ng-repeat="change in $ctrl.historyReview.items track by change.changeId"><td>{{change.effectiveDate | formatDate}}</td><td>{{$ctrl.dateTime(change.changedAt)}}</td><td class="money-cell">{{change.previousAmount | formatMoney:change.currency}}</td><td class="money-cell">{{change.newAmount | formatMoney:change.currency}}</td><td class="reason-cell">{{change.reason}}</td><td>{{change.actor.displayName}}</td></tr><tr ng-if="!$ctrl.historyReview.items.length"><td colspan="6" class="empty-row">No salary changes recorded. The initial salary is preserved above.</td></tr></tbody></table></div>
          <div class="employee-pagination" ng-if="$ctrl.historyReview.totalPages > 1"><p class="muted">Page {{$ctrl.historyReview.page + 1}} of {{$ctrl.historyReview.totalPages}}</p><div><button type="button" class="btn btn-secondary" ng-disabled="!$ctrl.historyReview.hasPrevious" ng-click="$ctrl.reviewHistory($ctrl.historyPage - 1)">Previous history page</button><button type="button" class="btn btn-secondary" ng-disabled="!$ctrl.historyReview.hasNext" ng-click="$ctrl.reviewHistory($ctrl.historyPage + 1)">Next history page</button></div></div>
        </div>
        <button type="button" class="btn btn-secondary" ng-click="$ctrl.historyReviewOpen = false">Close history</button>
      </section>
      <div ng-if="$ctrl.needsReview" class="alert alert-info"><p>Your draft has been preserved. Review the latest record before saving again.</p><button type="button" class="btn btn-secondary" ng-click="$ctrl.review()" ng-disabled="$ctrl.reviewLoading">Review latest record</button></div>
      <div class="review-panel" ng-if="$ctrl.latest"><h3>Current employee record</h3><dl class="review-list"><div><dt>Current annual salary</dt><dd>{{$ctrl.latest.annualBaseSalary | formatMoney:$ctrl.latest.currency}}</dd></div><div><dt>Latest effective date</dt><dd>{{$ctrl.latest.salaryEffectiveFrom | formatDate}}</dd></div><div><dt>Status</dt><dd>{{$ctrl.latest.employmentStatus}}</dd></div><div><dt>Joining / last employment</dt><dd>{{$ctrl.latest.joiningDate | formatDate}} / {{$ctrl.latest.lastEmploymentDate ? ($ctrl.latest.lastEmploymentDate | formatDate) : 'Open-ended'}}</dd></div></dl><p ng-if="$ctrl.uncertain" class="muted">This is current data, not confirmation of the original request. Retry the original change to confirm its recorded outcome.</p><button ng-if="!$ctrl.uncertain" type="button" class="btn btn-secondary" ng-click="$ctrl.acceptReview(salaryForm)">I’ve reviewed this record; keep my draft</button></div>
      <fieldset ng-disabled="$ctrl.saving || $ctrl.uncertain || $ctrl.employee.employmentStatus === 'INACTIVE'">
        <div class="salary-amount-row"><div class="field"><label for="annualBaseSalary">Annual base salary <span aria-hidden="true">*</span></label><input id="annualBaseSalary" name="annualBaseSalary" type="text" inputmode="decimal" ng-model="$ctrl.draft.annualBaseSalary" ng-model-options="{ allowInvalid: true }" ng-blur="$ctrl.validateField('annualBaseSalary', salaryForm.annualBaseSalary)" ng-change="$ctrl.clearValidation('annualBaseSalary', salaryForm.annualBaseSalary)" pattern="[0-9]+(\\.[0-9]+)?" required aria-describedby="salary-hint salary-amount-error" aria-invalid="{{$ctrl.fieldError('annualBaseSalary') ? 'true' : 'false'}}"><span id="salary-amount-error" class="field-error" aria-live="polite">{{$ctrl.fieldError('annualBaseSalary')}}</span></div><div class="field"><label for="salary-currency">Currency</label><input id="salary-currency" ng-value="$ctrl.employee.currency" readonly></div></div>
        <p class="field-hint" id="salary-hint">Currency is fixed. Use a positive amount without commas, with up to {{$ctrl.precision}} decimal places.</p>
        <div class="field"><label for="reason">Reason for change <span aria-hidden="true">*</span></label><textarea id="reason" name="reason" rows="4" maxlength="1000" ng-model="$ctrl.draft.reason" required aria-describedby="reason-hint"></textarea><span id="reason-hint" class="field-hint">This reason will be saved in the employee’s salary history.</span><span class="field-error">{{$ctrl.fieldError('reason')}}</span></div>
        <div class="field effective-date-field"><label for="effectiveDate">Effective from <span aria-hidden="true">*</span></label><input id="effectiveDate" name="effectiveDate" type="text" placeholder="YYYY-MM-DD" ng-model="$ctrl.draft.effectiveDate" ng-model-options="{ allowInvalid: true }" ng-blur="$ctrl.validateField('effectiveDate', salaryForm.effectiveDate)" ng-change="$ctrl.clearValidation('effectiveDate', salaryForm.effectiveDate)" pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}" required aria-describedby="effective-date-hint effective-date-error" aria-invalid="{{$ctrl.fieldError('effectiveDate') ? 'true' : 'false'}}"><span id="effective-date-error" class="field-error" aria-live="polite">{{$ctrl.fieldError('effectiveDate')}}</span></div>
        <p id="effective-date-hint" class="field-hint">Must be after {{$ctrl.employee.salaryEffectiveFrom | formatDate}}, on or before today ({{$ctrl.today | formatDate}}), and within employment dates.</p>
      </fieldset>
      <section class="salary-review"><h2 class="section-title">Review before saving</h2><div class="salary-comparison"><div><p class="muted">Current annual base salary</p><strong>{{$ctrl.employee.annualBaseSalary | formatMoney:$ctrl.employee.currency}}</strong></div><div><p class="muted">New annual base salary</p><strong class="text-blue">{{$ctrl.draft.annualBaseSalary ? ($ctrl.draft.annualBaseSalary | formatMoney:$ctrl.employee.currency) : 'Enter an amount'}}</strong></div></div><div class="alert alert-info"><strong>Whole-month application</strong><p>The salary selected by effective date applies to the entire month. Only joining or leaving prorates the monthly estimate.</p></div><dl class="review-list"><div><dt>Latest recorded effective date</dt><dd>{{$ctrl.employee.salaryEffectiveFrom | formatDate}}</dd></div><div><dt>Draft effective from</dt><dd>{{$ctrl.draft.effectiveDate || '—'}}</dd></div><div><dt>Joining date</dt><dd>{{$ctrl.employee.joiningDate | formatDate}}</dd></div><div><dt>Last employment date</dt><dd>{{$ctrl.employee.lastEmploymentDate ? ($ctrl.employee.lastEmploymentDate | formatDate) : 'Open-ended'}}</dd></div></dl></section>
      <div class="form-actions"><a class="btn btn-secondary" ng-href="#!/employees/{{$ctrl.id}}">Cancel</a><button class="btn btn-primary" type="submit" ng-disabled="$ctrl.saving || $ctrl.uncertain || $ctrl.needsReview || $ctrl.employee.employmentStatus !== 'ACTIVE'">{{$ctrl.saving ? 'Saving…' : 'Save salary'}}</button></div>
    </form>
    <aside class="card salary-aside"><h2 class="section-title">A recorded, reliable change</h2><p class="muted">Every saved salary change includes:</p><ul class="assurance-list"><li>Previous and new salary</li><li>Reason for the change</li><li>Who made the change</li><li>Effective date and recording time</li></ul><p class="muted">Your changes are saved together with their history.</p><p class="muted">Changes by another HR session must be reviewed before you save.</p></aside>
  </div>
</section>`;

function SalaryController(api, $routeParams, $location, $scope, $timeout) {
  const vm = this; const alive = setup(vm, $scope);
  let timer; let historyRequest = 0;
  $scope.$on('$destroy', () => { if (timer) $timeout.cancel(timer); });
  vm.id = $routeParams.id || $routeParams.employeeId; vm.today = today();
  vm.draft = { annualBaseSalary: '', reason: '', effectiveDate: '' }; vm.attempts = 0;
  vm.validationErrors = {};
  const serverFieldError = vm.fieldError;
  vm.fieldError = field => vm.validationErrors[field] || serverFieldError(field);
  vm.clearValidation = (field, control) => {
    vm.validationErrors[field] = null;
    control.$setValidity('salaryValue', true);
    if (vm.error?.fieldErrors?.some(issue => issue.field === field)) {
      const fieldErrors = vm.error.fieldErrors.filter(issue => issue.field !== field);
      vm.error = fieldErrors.length ? { ...vm.error, fieldErrors } : null;
    }
  };
  vm.validateField = (field, control) => {
    vm.today = today();
    const error = field === 'annualBaseSalary'
      ? validateSalaryAmount(vm.draft.annualBaseSalary, vm.precision)
      : validateSalaryEffectiveDate(vm.draft.effectiveDate, vm.employee, vm.today);
    vm.validationErrors[field] = error;
    control.$setValidity('salaryValue', !error);
    return !error;
  };
  vm.load = async () => {
    vm.loading = true; vm.loadError = null;
    try { const [employee, refs] = await Promise.all([api.get(employeePath(vm.id)), api.references()]); if (alive()) { vm.employee = employee; vm.precision = Math.min(2, refs.currencies.find(item => item.code === employee.currency)?.fractionDigits ?? 2); } }
    catch (error) { vm.loadError = error; }
    finally { vm.loading = false; vm.paint(); }
  };
  const submit = async () => {
    if (!vm.operation || vm.saving || vm.retryWait || vm.attempts >= 3) return;
    vm.saving = true; vm.error = null; vm.attempts++;
    try {
      await api.send('POST', `${employeePath(vm.id)}/salary-changes`, vm.operation.body, { idempotencyKey: vm.operation.key });
      vm.operation = null; vm.uncertain = false;
      if (alive()) $location.path(`/employees/${vm.id}`).search({ saved: 'salary' });
    } catch (error) {
      vm.uncertain = Boolean(error.uncertain || !error.status || error.status >= 500);
      vm.needsReview = ['VERSION_CONFLICT', 'EMPLOYEE_INACTIVE'].includes(error.code);
      if (!vm.uncertain) vm.operation = null;
      if (vm.uncertain && error.retryAfter > 0) { vm.retryWait = true; timer = $timeout(() => { vm.retryWait = false; }, error.retryAfter * 1000); }
      vm.fail(error);
    } finally { vm.saving = false; vm.paint(); }
  };
  vm.save = form => {
    if (vm.saving || vm.uncertain || !vm.employee || vm.employee.employmentStatus !== 'ACTIVE' || vm.needsReview) return;
    const amountValid = vm.validateField('annualBaseSalary', form.annualBaseSalary);
    const dateValid = vm.validateField('effectiveDate', form.effectiveDate);
    if (!amountValid || !dateValid) { form.$setSubmitted(); return; }
    if (form.$invalid || !vm.draft.reason.trim()) { form.$setSubmitted(); vm.fail({ message: 'Enter an annual salary, a valid effective date, and a reason.', fieldErrors: [] }); return; }
    vm.operation = { key: crypto.randomUUID(), body: Object.freeze(buildSalaryPayload(vm.draft, vm.employee)) }; vm.attempts = 0;
    submit();
  };
  vm.retry = () => { if (vm.uncertain) submit(); };
  vm.review = async () => {
    vm.reviewLoading = true;
    try { vm.latest = await api.get(employeePath(vm.id)); }
    catch (error) { vm.fail(error); }
    finally { vm.reviewLoading = false; vm.paint(); }
  };
  vm.reviewHistory = async (page = 0) => {
    const serial = ++historyRequest;
    vm.historyReviewOpen = true; vm.historyLoading = true; vm.historyError = null; vm.historyPage = page;
    try {
      const history = await api.get(`${employeePath(vm.id)}/salary-changes`, { page, size: 25 });
      if (serial === historyRequest && alive()) vm.historyReview = history;
    } catch (error) { if (serial === historyRequest) vm.historyError = error; }
    finally { if (serial === historyRequest) vm.historyLoading = false; vm.paint(); }
  };
  vm.acceptReview = form => {
    if (vm.uncertain) return;
    vm.employee = vm.latest; vm.latest = null; vm.error = null; vm.needsReview = false; vm.operation = null;
    for (const field of ['annualBaseSalary', 'effectiveDate']) {
      if (form[field].$touched) vm.validateField(field, form[field]);
    }
  };
  vm.$onInit = vm.load;
}

const fieldError = name => `<span class="field-error">{{$ctrl.fieldError('${name}')}}</span>`;
const textField = (name, label, options = '') => `<div class="field"><label for="${name}">${label} <span aria-hidden="true">*</span></label><input id="${name}" name="${name}" type="text" ng-model="$ctrl.draft.${name}" required ${options}>${fieldError(name)}</div>`;
const selectField = (name, label, source, key) => `<div class="field"><label for="${name}">${label} <span aria-hidden="true">*</span></label><select id="${name}" name="${name}" ng-model="$ctrl.draft.${name}" ng-options="item.${key} as item.label for item in $ctrl.refs.${source}" required><option value="">Choose ${label.toLowerCase()}</option></select>${fieldError(name)}</div>`;

const formTemplate = `
<section class="employee-page">
  <div class="page-header"><div><h1 class="page-title">{{$ctrl.editing ? 'Edit employee' : 'Add employee'}}</h1><p class="muted">{{$ctrl.editing ? ($ctrl.employee.fullName + ' / ' + $ctrl.id) : 'Create an employee record and set their annual base salary.'}}</p></div></div>
  ${loadState}
  <form ng-if="$ctrl.ready && !$ctrl.loading && !$ctrl.loadError" class="card employee-form" name="employeeForm" ng-submit="$ctrl.save(employeeForm)" novalidate>
    <div class="form-section-heading"><h2 class="section-title">{{$ctrl.editing ? 'Employee information' : 'Employee information and compensation'}}</h2><span class="muted">* Required fields</span></div>
    ${errorState}
    <div ng-if="$ctrl.uncertain || $ctrl.needsReview" class="alert alert-info"><p>Your draft is preserved. Review the current record before submitting again.</p><button type="button" class="btn btn-secondary" ng-click="$ctrl.review()" ng-disabled="$ctrl.reviewLoading">{{$ctrl.reviewLoading ? 'Loading record…' : 'Review current record'}}</button></div>
    <div class="review-panel" ng-if="$ctrl.latest"><h3>Review current details against your draft</h3><div class="table-wrap"><table class="data-table"><thead><tr><th>Field</th><th>Current record</th><th>Your draft</th></tr></thead><tbody><tr ng-repeat="field in $ctrl.reviewFields"><th scope="row">{{field.label}}</th><td>{{$ctrl.latest[field.key] || '—'}}</td><td>{{$ctrl.draft[field.key] || '—'}}</td></tr></tbody></table></div><p class="muted">The existence of this record alone does not confirm which request created or changed it.</p><button ng-if="$ctrl.editing" type="button" class="btn btn-secondary" ng-click="$ctrl.acceptReview()">I’ve reviewed this record; keep my draft</button><a class="btn btn-secondary" ng-href="#!/employees/{{$ctrl.latest.employeeId}}">Open current employee</a></div>
    <div ng-if="$ctrl.notice" class="alert alert-info" role="status">{{$ctrl.notice}}</div>
    <fieldset ng-disabled="$ctrl.saving || $ctrl.uncertain">
      <div class="form-grid employee-fields">
        ${textField('employeeId', 'Employee ID', 'ng-readonly="$ctrl.editing" placeholder="EMP-000001" pattern="EMP-[0-9]{6}"')}
        ${textField('fullName', 'Full name', 'maxlength="200" autocomplete="name"')}
        <div class="field"><label for="workEmail">Work email <span aria-hidden="true">*</span></label><input id="workEmail" name="workEmail" type="email" ng-model="$ctrl.draft.workEmail" maxlength="254" autocomplete="email" required>${fieldError('workEmail')}</div>
        ${selectField('countryCode', 'Country', 'countries', 'code')}
        ${selectField('department', 'Department', 'departments', 'value')}
        ${textField('jobTitle', 'Job title', 'maxlength="200"')}
        ${selectField('jobLevel', 'Job level', 'jobLevels', 'value')}
        <div class="field"><label for="employmentStatus">Employment status</label><input id="employmentStatus" ng-value="$ctrl.employee.employmentStatus === 'INACTIVE' ? 'Inactive' : 'Active'" readonly></div>
        <div class="field" ng-if="!$ctrl.editing"><label for="annualBaseSalary">Annual base salary <span aria-hidden="true">*</span></label><input id="annualBaseSalary" name="annualBaseSalary" type="text" inputmode="decimal" ng-model="$ctrl.draft.annualBaseSalary" pattern="[0-9]+(\\.[0-9]+)?" required><span class="field-hint">Use a positive amount without commas.</span>${fieldError('annualBaseSalary')}</div>
        <div class="field" ng-if="!$ctrl.editing"><label for="currency">Currency <span aria-hidden="true">*</span></label><select id="currency" name="currency" ng-model="$ctrl.draft.currency" ng-options="item.code as (item.code + ' — ' + item.label) for item in $ctrl.refs.currencies" required><option value="">Choose currency</option></select>${fieldError('currency')}</div>
        ${textField('joiningDate', 'Joining date', 'placeholder="YYYY-MM-DD" pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}" ng-change="$ctrl.joiningChanged()"')}
        <div class="field" ng-if="!$ctrl.editing"><label for="initialSalaryEffectiveFrom">Initial salary effective from <span aria-hidden="true">*</span></label><input id="initialSalaryEffectiveFrom" name="initialSalaryEffectiveFrom" type="text" placeholder="YYYY-MM-DD" ng-model="$ctrl.draft.initialSalaryEffectiveFrom" ng-change="$ctrl.initialDateEdited = true" pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}" required>${fieldError('initialSalaryEffectiveFrom')}</div>
        <div class="field" ng-if="$ctrl.editing"><label for="lastEmploymentDate">Last employment date</label><input id="lastEmploymentDate" name="lastEmploymentDate" type="text" placeholder="YYYY-MM-DD or leave empty" ng-model="$ctrl.draft.lastEmploymentDate" pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"><span class="field-hint">Inclusive final day. A future date does not mark the employee inactive.</span>${fieldError('lastEmploymentDate')}</div>
        <div class="field" ng-if="$ctrl.editing"><label>Initial salary effective from</label><output class="readonly-value">{{$ctrl.employee.initialSalaryEffectiveFrom | formatDate}}</output></div><div class="field" ng-if="$ctrl.editing"><label>Latest salary effective</label><output class="readonly-value">{{$ctrl.employee.salaryEffectiveFrom | formatDate}}</output></div>
        <div class="field" ng-if="$ctrl.editing"><label>Annual salary</label><output class="readonly-value">{{$ctrl.employee.annualBaseSalary | formatMoney:$ctrl.employee.currency}}</output></div><div class="field" ng-if="$ctrl.editing"><label>Currency</label><output class="readonly-value">{{$ctrl.employee.currency}}</output></div>
      </div>
    </fieldset>
    <div class="alert alert-info" ng-if="$ctrl.editing"><strong>Salary changes have their own history</strong><p>Use Update salary to change compensation and record a reason. Employee ID and currency cannot be changed.</p></div>
    <p class="field-hint" ng-if="!$ctrl.editing">Joining date must be on or before today. Initial salary effective date defaults to joining date and must fall between joining date and today. Currency is fixed after creation.</p>
    <div class="form-actions"><a class="btn btn-secondary" ng-href="{{$ctrl.editing ? '#!/employees/' + $ctrl.id : '#!/employees'}}">Cancel</a><button class="btn btn-primary" type="submit" ng-disabled="$ctrl.saving || $ctrl.uncertain || $ctrl.needsReview">{{$ctrl.saving ? 'Saving…' : ($ctrl.editing ? 'Save changes' : 'Save employee')}}</button></div>
  </form>
</section>`;

function FormController(api, $routeParams, $location, $scope) {
  const vm = this; const alive = setup(vm, $scope);
  vm.id = $routeParams.id || $routeParams.employeeId; vm.editing = Boolean(vm.id && vm.id !== 'new'); vm.refs = {};
  vm.reviewFields = [{ key: 'fullName', label: 'Name' }, { key: 'workEmail', label: 'Work email' }, { key: 'countryCode', label: 'Country' }, { key: 'department', label: 'Department' }, { key: 'jobTitle', label: 'Job title' }, { key: 'jobLevel', label: 'Job level' }, { key: 'joiningDate', label: 'Joining date' }, { key: 'lastEmploymentDate', label: 'Last employment date' }];
  vm.draft = { employeeId: '', fullName: '', workEmail: '', countryCode: '', department: '', jobTitle: '', jobLevel: '', currency: '', annualBaseSalary: '', joiningDate: today(), initialSalaryEffectiveFrom: today(), lastEmploymentDate: '' };
  vm.load = async () => {
    vm.loading = true; vm.loadError = null;
    try {
      const [refs, employee] = await Promise.all([api.references(), vm.editing ? api.get(employeePath(vm.id)) : Promise.resolve(null)]);
      if (alive()) { vm.refs = refs; vm.employee = employee; if (employee && !vm.ready) vm.draft = { ...employee, lastEmploymentDate: employee.lastEmploymentDate || '' }; vm.ready = true; }
    } catch (error) { vm.loadError = error; }
    finally { vm.loading = false; vm.paint(); }
  };
  vm.joiningChanged = () => { if (!vm.editing && !vm.initialDateEdited) vm.draft.initialSalaryEffectiveFrom = vm.draft.joiningDate; };
  vm.save = async form => {
    if (vm.saving || vm.uncertain || vm.needsReview) return;
    if (form.$invalid) { form.$setSubmitted(); vm.fail({ message: 'Complete the required fields and use dates in YYYY-MM-DD format.', fieldErrors: [] }); return; }
    vm.saving = true; vm.error = null; vm.latest = null; vm.notice = null;
    const id = vm.editing ? vm.id : vm.draft.employeeId;
    try {
      const employee = await api.send(vm.editing ? 'PUT' : 'POST', vm.editing ? `${employeePath(vm.id)}/details` : '/employees', buildEmployeePayload(vm.draft, vm.editing ? vm.employee : null));
      if (alive()) $location.path(`/employees/${employee.employeeId}`).search({ saved: vm.editing ? 'details' : 'created' });
    } catch (error) { vm.uncertain = Boolean(error.uncertain || !error.status || error.status >= 500); vm.needsReview = error.code === 'VERSION_CONFLICT'; vm.pendingId = id; vm.fail(error); }
    finally { vm.saving = false; vm.paint(); }
  };
  vm.review = async () => {
    vm.reviewLoading = true;
    try { vm.latest = await api.get(employeePath(vm.pendingId || vm.id || vm.draft.employeeId)); }
    catch (error) {
      if (!vm.editing && error.code === 'EMPLOYEE_NOT_FOUND') { vm.uncertain = false; vm.error = null; vm.notice = 'No record was found for this employee ID. You may submit the same draft again; a late original save could still produce an ID conflict.'; }
      else vm.fail(error);
    } finally { vm.reviewLoading = false; vm.paint(); }
  };
  vm.acceptReview = () => { vm.employee = vm.latest; vm.latest = null; vm.uncertain = false; vm.needsReview = false; vm.error = null; vm.notice = 'Current details reviewed. Your draft is retained; review all fields before saving.'; };
  vm.$onInit = vm.load;
}

export function registerEmployeePages(app) {
  app.component('employeeDetail', { template: detailTemplate, controller: ['api', '$routeParams', '$location', '$scope', '$mdDialog', DetailController] });
  app.component('employeeHistory', { template: historyTemplate, controller: ['api', '$routeParams', '$scope', HistoryController] });
  app.component('employeeSalary', { template: salaryTemplate, controller: ['api', '$routeParams', '$location', '$scope', '$timeout', SalaryController] });
  app.component('employeeForm', { template: formTemplate, controller: ['api', '$routeParams', '$location', '$scope', FormController] });
}

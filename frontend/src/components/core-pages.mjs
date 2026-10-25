const shield = '<svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6z"/><path d="m8 12 3 3 5-6"/></svg>';
const people = '<svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 4a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 4v3"/></svg>';
const chart = '<svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 3v17h17M9 16V9m5 7V5m5 11v-5"/></svg>';
const brand = '<div class="brand"><span class="brand-mark" aria-hidden="true">A</span><span>ACME</span></div>';
const loading = '<div class="loading-state" ng-if="$ctrl.loading" role="status"><span class="spinner" aria-hidden="true"></span>Loading…</div>';
const requestError = `<div ng-if="$ctrl.error" class="alert alert-error" role="alert" tabindex="-1"><p>{{$ctrl.error.message}}</p><span class="request-id" ng-if="$ctrl.error.requestId">Reference: {{$ctrl.error.requestId}}</span><div class="actions"><button class="btn btn-secondary" ng-click="$ctrl.load()">Retry</button></div></div>`;
const referenceError = `<div ng-if="$ctrl.referenceError" class="alert alert-error" role="alert"><strong>Filters are temporarily unavailable.</strong><p>{{$ctrl.referenceError.message}}</p><button class="btn btn-secondary" ng-click="$ctrl.loadReferences()">Retry filters</button></div>`;
const categories = [
  { key: 'country', label: 'Country', source: 'countries', empty: 'All countries' },
  { key: 'department', label: 'Department', source: 'departments', empty: 'All departments' },
  { key: 'jobLevel', label: 'Job level', source: 'jobLevels', empty: 'All levels' },
  { key: 'currency', label: 'Currency', source: 'currencies', empty: 'All currencies' },
];
const emptyFilters = () => ({ country: [], department: [], jobLevel: [], currency: [] });
const filterControls = (automatic = false) => categories.map(({ key, label, source, empty }) => `
  <md-input-container><label>${label}</label><md-select ng-model="$ctrl.filters.${key}" multiple aria-label="${label}" placeholder="${empty}" ng-disabled="!$ctrl.refs" ${automatic ? `id="directory-filter-${key}" ng-blur="$ctrl.filterBlur('${key}')" md-on-open="$ctrl.openFilter = '${key}'" md-on-close="$ctrl.filterClosed('${key}')"` : ''}>
    <md-option ng-repeat="option in $ctrl.refs.${source}" ng-value="option.code || option.value">{{option.label}}</md-option>
  </md-select></md-input-container>`).join('');

function paintable(vm, $scope) {
  let alive = true;
  $scope.$on('$destroy', () => { alive = false; });
  vm.paint = () => { if (alive) $scope.$applyAsync(); };
  return () => alive;
}

function ShellController(api, $rootScope, $scope, $location, $route, sessionGate) {
  const vm = this;
  vm.api = api;
  vm.routeLoading = true;
  vm.isReports = () => $location.path().startsWith('/reports');
  vm.breadcrumb = () => vm.isReports() ? 'Compensation reports' : 'Employees';
  vm.closeMenu = () => { vm.menuOpen = false; };
  vm.skipToContent = event => { event.preventDefault(); document.getElementById('main-content')?.focus(); };
  const unsubscribe = [
    $rootScope.$on('$routeChangeStart', () => { vm.routeLoading = true; vm.bootError = null; vm.menuOpen = false; }),
    $rootScope.$on('$routeChangeSuccess', () => {
      vm.routeLoading = false;
      $rootScope.sessionEstablished = Boolean(api.user);
      document.title = `ACME · ${api.user ? vm.breadcrumb() : 'Sign in'}`;
    }),
    $rootScope.$on('$routeChangeError', (_event, _next, _previous, error) => {
      vm.routeLoading = false;
      if (error.code === 'SIGN_IN_REQUIRED' || error.status === 401 || (error.status === 403 && error.code === 'ACCESS_DENIED')) {
        $location.path('/sign-in');
      } else vm.bootError = error;
    }),
  ];
  $scope.$on('$destroy', () => unsubscribe.forEach(fn => fn()));
  vm.retrySession = () => { vm.bootError = null; sessionGate.reset(); $route.reload(); };
  vm.logout = async () => {
    if (vm.signingOut) return;
    vm.signingOut = true; vm.logoutError = null;
    try { await api.logout(); $scope.$evalAsync(() => { $rootScope.accessMessage = null; $location.path('/sign-in'); }); }
    catch (error) { vm.logoutError = error; }
    finally { vm.signingOut = false; $scope.$applyAsync(); }
  };
}
ShellController.$inject = ['api', '$rootScope', '$scope', '$location', '$route', 'sessionGate'];

const shellTemplate = `
<a href="#main-content" class="skip-link" ng-click="$ctrl.skipToContent($event)">Skip to content</a>
<div ng-class="{'workspace': $ctrl.api.user}">
  <div class="sidebar-scrim" ng-if="$ctrl.api.user && $ctrl.menuOpen" ng-click="$ctrl.closeMenu()" aria-hidden="true"></div>
  <aside class="sidebar" ng-if="$ctrl.api.user" ng-class="{'open': $ctrl.menuOpen}" aria-label="Workspace navigation">
    <a class="brand" href="#!/employees"><span class="brand-mark" aria-hidden="true">A</span><span>ACME</span></a><div class="eyebrow">People operations</div><div class="organization">ACME Organization<small>Salary management</small></div>
    <nav class="nav-links"><a class="nav-link" href="#!/employees" ng-class="{'active': !$ctrl.isReports()}" ng-attr-aria-current="{{!$ctrl.isReports() ? 'page' : undefined}}">${people}Employees</a><a class="nav-link" href="#!/reports" ng-class="{'active': $ctrl.isReports()}" ng-attr-aria-current="{{$ctrl.isReports() ? 'page' : undefined}}">${chart}Reports</a></nav>
    <div class="sidebar-user"><strong>{{$ctrl.api.user.displayName}}</strong><small>HR Manager</small><button class="signout" ng-click="$ctrl.logout()" ng-disabled="$ctrl.signingOut">{{$ctrl.signingOut ? 'Signing out…' : 'Sign out'}}</button></div>
  </aside>
  <main id="main-content" ng-class="{'workspace-main': $ctrl.api.user}" tabindex="-1">
    <header class="topbar" ng-if="$ctrl.api.user"><button class="mobile-menu" aria-label="Toggle navigation" ng-click="$ctrl.menuOpen = !$ctrl.menuOpen" ng-attr-aria-expanded="{{$ctrl.menuOpen || false}}">☰</button><span>Workspace / {{$ctrl.breadcrumb()}}</span><span class="workspace-label">${shield}HR workspace</span></header>
    <div class="alert alert-error logout-alert" ng-if="$ctrl.logoutError && $ctrl.api.user" role="alert"><strong>Sign-out could not be confirmed.</strong><p>{{$ctrl.logoutError.message}}</p><p>You can try signing out again.</p></div>
    <div ng-if="$ctrl.bootError" class="boot-state">${brand}<div class="card"><h1>Unable to open the workspace</h1><p>{{$ctrl.bootError.message}}</p><button class="btn btn-primary" ng-click="$ctrl.retrySession()">Retry</button></div></div>
    <div ng-if="$ctrl.routeLoading && !$ctrl.api.user && !$ctrl.bootError" class="boot-state" role="status">${brand}<span class="spinner" aria-hidden="true"></span><span>Opening your workspace…</span></div>
    <div ng-class="{'page-content': $ctrl.api.user}" ng-show="!$ctrl.bootError && !$ctrl.routeLoading"><div ng-view></div></div>
  </main>
</div>`;

function SignInController(api, $scope, $rootScope, $location, $interval) {
  const vm = this; vm.email = ''; vm.password = ''; vm.accessMessage = $rootScope.accessMessage;
  vm.$onInit = () => { if (api.user) $location.path('/employees'); };
  const ticker = $interval(() => { vm.remaining = Math.max(0, Math.ceil(((vm.retryAt || 0) - Date.now()) / 1000)); }, 1000);
  vm.$onDestroy = () => { vm.password = ''; $interval.cancel(ticker); };
  vm.submit = async form => {
    vm.submitted = true;
    if (vm.busy || vm.remaining || form.$invalid) return;
    vm.busy = true; vm.error = null;
    try { await api.login(vm.email, vm.password); $scope.$evalAsync(() => { $rootScope.accessMessage = null; $location.path('/employees'); }); }
    catch (error) { vm.error = error; if (error.retryAfter) { vm.remaining = error.retryAfter; vm.retryAt = Date.now() + error.retryAfter * 1000; } }
    finally { vm.password = ''; vm.busy = false; $scope.$applyAsync(); }
  };
}
SignInController.$inject = ['api', '$scope', '$rootScope', '$location', '$interval'];
const signinTemplate = `
<section class="signin-layout">
  <aside class="signin-story">${brand}<div class="signin-copy"><h1>People.<br>Clarity.<br>Confidence.</h1><p class="lead">One trusted place for compensation decisions.</p><p class="description">Maintain salary records and understand how your organization pays people across countries.</p><div class="signin-benefits"><span>${shield}10,000 employee records</span><span>${shield}Compensation insights by currency</span><span>${shield}Every salary change accounted for</span></div></div><footer>ACME / PEOPLE OPERATIONS</footer></aside>
  <div class="signin-main"><div class="signin-inner"><form class="card signin-card" name="signInForm" ng-submit="$ctrl.submit(signInForm)" novalidate>
    <h1>Sign in to ACME</h1><p>Sign in to your HR workspace.</p>
    <div ng-if="$ctrl.accessMessage" class="alert alert-warning" role="status">{{$ctrl.accessMessage}}</div>
    <div ng-if="$ctrl.error" class="alert alert-error" role="alert"><span>{{$ctrl.error.message}}</span><span ng-if="$ctrl.remaining"> Try again in {{$ctrl.remaining}} seconds.</span><span class="request-id" ng-if="$ctrl.error.requestId">Reference: {{$ctrl.error.requestId}}</span></div>
    <div class="field"><label for="signin-email">Work email</label><input id="signin-email" name="email" type="email" ng-model="$ctrl.email" ng-model-options="{allowInvalid: true}" autocomplete="username" placeholder="you@acme.example" maxlength="254" required aria-describedby="email-error"><span id="email-error" class="field-error" ng-if="$ctrl.submitted && signInForm.email.$invalid">Enter a valid work email.</span></div>
    <div class="field"><label for="signin-password">Password</label><input id="signin-password" name="password" type="password" ng-model="$ctrl.password" ng-trim="false" autocomplete="current-password" maxlength="128" required aria-describedby="password-error"><span id="password-error" class="field-error" ng-if="$ctrl.submitted && ! $ctrl.error && signInForm.password.$invalid">Password is required.</span></div>
    <button type="submit" class="btn btn-primary" ng-disabled="$ctrl.busy || $ctrl.remaining">{{$ctrl.busy ? 'Signing in…' : 'Sign in'}}</button><div class="signin-restriction">${shield}Authorized HR users only</div>
  </form><p class="signin-footer">Salary management · ACME Organization</p></div></div>
</section>`;

function initReferences(vm, api) {
  vm.loadReferences = async () => {
    vm.refs = null; vm.referenceError = null;
    try { vm.refs = await api.references(); }
    catch (error) { vm.referenceError = error; }
    finally { vm.paint(); }
  };
  vm.label = (kind, value) => (vm.refs?.[kind] || []).find(item => (item.code || item.value) === value)?.label || value;
}

function DirectoryController(api, $scope, $timeout, $location, directoryState) {
  const vm = this; const alive = paintable(vm, $scope); let sequence = 0;
  const copyFilters = filters => Object.fromEntries(categories.map(({ key }) => [key, [...filters[key]]]));
  const saved = directoryState.owner === api.user ? directoryState.value : null;
  vm.filters = copyFilters(saved?.filters || emptyFilters());
  vm.appliedFilters = copyFilters(saved?.appliedFilters || emptyFilters());
  vm.page = saved?.page || 0; vm.size = 100; vm.sort = saved?.sort || 'fullName,asc';
  vm.q = saved?.q || ''; vm.appliedQ = saved?.appliedQ || ''; vm.pageInput = String(vm.page + 1);
  vm.suggestions = []; vm.activeSuggestion = -1;
  let suggestionSequence = 0; let suggestionTimer; let restoreScroll = saved?.scroll;
  const pendingBlur = new Map();
  initReferences(vm, api);
  vm.load = async () => {
    const current = ++sequence; vm.loading = true; vm.error = null; vm.result = null;
    const query = { ...vm.appliedFilters, page: vm.page, size: vm.size, sort: vm.sort };
    if (vm.appliedQ) query.q = vm.appliedQ;
    try {
      const result = await api.get('/employees', query);
      if (!alive() || current !== sequence) return;
      const lastPage = Math.max(0, result.totalPages - 1);
      if (vm.page > lastPage) { vm.page = lastPage; return vm.load(); }
      vm.result = result; vm.pageInput = String(vm.page + 1);
      if (restoreScroll !== undefined) {
        const scroll = restoreScroll; restoreScroll = undefined;
        $timeout(() => requestAnimationFrame(() => {
          if (alive() && current === sequence) window.scrollTo(0, scroll);
        }));
      }
    }
    catch (error) { if (alive() && current === sequence) vm.error = error; }
    finally { if (current === sequence) { vm.loading = false; vm.paint(); } }
  };
  vm.apply = () => { vm.page = 0; vm.pageError = null; vm.load(); };
  vm.closeSuggestions = () => {
    ++suggestionSequence; $timeout.cancel(suggestionTimer);
    vm.suggestions = []; vm.activeSuggestion = -1;
  };
  vm.searchChanged = () => {
    vm.closeSuggestions();
    const prefix = vm.q.trim();
    if (!prefix) return;
    const current = suggestionSequence;
    suggestionTimer = $timeout(async () => {
      try {
        const suggestions = await api.get('/employees/suggestions', { ...vm.appliedFilters, prefix, limit: 8 });
        if (alive() && current === suggestionSequence) vm.suggestions = suggestions;
      } catch {
        // Lookup failures leave the existing table and its search available.
        if (alive() && current === suggestionSequence) vm.suggestions = [];
      } finally { vm.paint(); }
    }, 300);
  };
  vm.applySearch = () => {
    vm.closeSuggestions();
    if (vm.q.trim() === vm.appliedQ) return;
    vm.appliedQ = vm.q.trim(); vm.apply();
  };
  vm.searchBlur = event => {
    if (event.relatedTarget?.closest('#employee-suggestions')) return;
    if (event.relatedTarget?.closest('[data-clear-filters]')) { vm.closeSuggestions(); return; }
    vm.applySearch();
  };
  vm.chooseSuggestion = employee => {
    vm.closeSuggestions();
    $location.path(`/employees/${employee.employeeId}`);
  };
  vm.searchKey = event => {
    if (event.key === 'Escape') { event.preventDefault(); vm.closeSuggestions(); }
    else if (['ArrowDown', 'ArrowUp'].includes(event.key) && vm.suggestions.length) {
      event.preventDefault();
      const count = vm.suggestions.length;
      vm.activeSuggestion = event.key === 'ArrowDown'
        ? (vm.activeSuggestion + 1) % count
        : (vm.activeSuggestion <= 0 ? count - 1 : vm.activeSuggestion - 1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (vm.activeSuggestion >= 0) vm.chooseSuggestion(vm.suggestions[vm.activeSuggestion]);
      else vm.applySearch();
    }
  };
  vm.filterBlur = key => {
    $timeout.cancel(pendingBlur.get(key));
    pendingBlur.set(key, $timeout(() => {
      const control = document.getElementById(`directory-filter-${key}`);
      // Material moves its popup under body and restores focus after closing it.
      if (!alive() || vm.openFilter === key || control?.contains(document.activeElement)) return;
      if (document.activeElement?.closest('[data-clear-filters]')) return;
      const values = [...vm.filters[key]].sort();
      if (JSON.stringify(values) === JSON.stringify([...vm.appliedFilters[key]].sort())) return;
      vm.appliedFilters[key] = values; vm.apply();
    }));
  };
  vm.filterClosed = key => { vm.openFilter = null; vm.filterBlur(key); };
  vm.clear = () => {
    vm.closeSuggestions(); vm.filters = emptyFilters(); vm.appliedFilters = emptyFilters();
    vm.q = ''; vm.appliedQ = ''; vm.apply();
  };
  vm.next = delta => { vm.page += delta; vm.pageError = null; vm.load(); };
  vm.goToPage = () => {
    const value = String(vm.pageInput).trim(); const page = Number(value);
    if (!/^\d+$/.test(value) || !Number.isSafeInteger(page) || page < 1 || page > vm.result.totalPages) {
      vm.pageError = `Enter a whole page number from 1 to ${vm.result.totalPages}.`; return;
    }
    vm.pageError = null;
    if (page - 1 !== vm.page) { vm.page = page - 1; vm.load(); }
  };
  vm.sortBy = field => { vm.sort = `${field},${vm.sort === `${field},asc` ? 'desc' : 'asc'}`; vm.apply(); };
  $scope.$on('$routeChangeStart', () => {
    directoryState.owner = api.user;
    directoryState.value = api.user ? {
      q: vm.q, appliedQ: vm.appliedQ, filters: copyFilters(vm.filters), appliedFilters: copyFilters(vm.appliedFilters),
      sort: vm.sort, page: vm.page, scroll: window.scrollY,
    } : null;
  });
  vm.$onDestroy = () => {
    vm.closeSuggestions(); pendingBlur.forEach(timer => $timeout.cancel(timer));
  };
  vm.$onInit = () => { vm.loadReferences(); vm.load(); };
}
DirectoryController.$inject = ['api', '$scope', '$timeout', '$location', 'directoryState'];
const directoryTemplate = `
<section><div class="page-header"><div><h1>Employee directory</h1><p>Find people, maintain salary records, and keep your organization up to date.</p></div><a class="btn btn-primary" href="#!/employees/new"><span aria-hidden="true">+</span> Add employee</a></div>
  ${referenceError}
  <div class="card filter-card"><div class="search-row"><div class="search-box"><label class="sr-only" for="employee-search">Search by name or employee ID</label><input id="employee-search" class="search-input" type="search" role="combobox" autocomplete="off" aria-autocomplete="list" aria-controls="employee-suggestions" ng-attr-aria-expanded="{{$ctrl.suggestions.length > 0}}" ng-attr-aria-activedescendant="{{$ctrl.activeSuggestion >= 0 ? 'employee-suggestion-' + $ctrl.activeSuggestion : undefined}}" ng-model="$ctrl.q" ng-change="$ctrl.searchChanged()" ng-blur="$ctrl.searchBlur($event)" ng-keydown="$ctrl.searchKey($event)" placeholder="Search by name or employee ID" maxlength="100"><div id="employee-suggestions" class="employee-suggestions" role="listbox" aria-label="Employee suggestions" ng-if="$ctrl.suggestions.length"><button type="button" role="option" tabindex="-1" ng-repeat="employee in $ctrl.suggestions track by employee.employeeId" id="employee-suggestion-{{$index}}" ng-attr-aria-selected="{{$index === $ctrl.activeSuggestion}}" ng-mousedown="$event.preventDefault()" ng-click="$ctrl.chooseSuggestion(employee)">{{employee.fullName}}</button></div></div><small ng-if="$ctrl.result">{{$ctrl.result.totalElements | number}} employees · All statuses</small></div><div class="filter-grid">${filterControls(true)}<button class="btn btn-secondary" type="button" data-clear-filters ng-click="$ctrl.clear()">Clear filters</button></div></div>
  ${loading}${requestError}
  <div ng-if="$ctrl.result && !$ctrl.loading">
    <div class="card empty-state" ng-if="!$ctrl.result.items.length"><h2>No matching employees</h2><p>Try a different search or clear your filters.</p><button class="btn btn-secondary" ng-click="$ctrl.clear()">Clear filters</button></div>
    <div class="table-wrap" ng-if="$ctrl.result.items.length"><table class="data-table"><thead><tr><th><button class="table-sort" ng-click="$ctrl.sortBy('fullName')">Employee</button></th><th><button class="table-sort" ng-click="$ctrl.sortBy('countryCode')">Country</button></th><th><button class="table-sort" ng-click="$ctrl.sortBy('department')">Department</button></th><th><button class="table-sort" ng-click="$ctrl.sortBy('jobLevel')">Level</button></th><th class="number">Annual base salary</th><th>Status</th><th><span class="sr-only">Open employee</span></th></tr></thead><tbody><tr ng-repeat="employee in $ctrl.result.items track by employee.employeeId"><td><a class="employee-link" ng-href="#!/employees/{{employee.employeeId}}" ng-mousedown="$event.button === 0 && $event.preventDefault()">{{employee.fullName}}</a><small>{{employee.employeeId}}</small></td><td>{{$ctrl.label('countries', employee.countryCode)}}</td><td>{{$ctrl.label('departments', employee.department)}}</td><td>{{employee.jobLevel}}</td><td class="number">{{employee.annualBaseSalary | formatMoney:employee.currency}}</td><td><span class="badge" ng-class="{'badge-inactive': employee.employmentStatus === 'INACTIVE'}">{{employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'}}</span></td><td><a ng-href="#!/employees/{{employee.employeeId}}" ng-mousedown="$event.button === 0 && $event.preventDefault()" aria-label="View details for {{employee.fullName}}">View details →</a></td></tr></tbody></table></div>
    <div class="pagination"><span ng-if="$ctrl.result.items.length">Showing {{$ctrl.result.page * $ctrl.result.size + 1 | number}}–{{$ctrl.result.page * $ctrl.result.size + $ctrl.result.items.length | number}} of {{$ctrl.result.totalElements | number}} employees · 100 per page</span><span ng-if="!$ctrl.result.items.length">{{$ctrl.result.totalElements | number}} matching employees</span><form class="actions page-jump" ng-if="$ctrl.result.items.length" ng-submit="$ctrl.goToPage()" novalidate><label for="directory-page">Page</label><input id="directory-page" type="text" inputmode="numeric" aria-label="Page number" ng-model="$ctrl.pageInput" ng-attr-aria-invalid="{{!!$ctrl.pageError}}" aria-describedby="directory-page-error"><span>of {{$ctrl.result.totalPages}}</span><button class="btn btn-small" type="submit">Go</button><button class="btn btn-small" type="button" ng-click="$ctrl.next(-1)" ng-disabled="!$ctrl.result.hasPrevious">Previous</button><button class="btn btn-small" type="button" ng-click="$ctrl.next(1)" ng-disabled="!$ctrl.result.hasNext">Next</button></form></div><p id="directory-page-error" class="field-error" role="alert" ng-if="$ctrl.pageError">{{$ctrl.pageError}}</p>
  </div><p class="help-note">All compensation amounts represent annual base salary. Currencies are not converted.</p>
</section>`;

function ReportsController(api, $scope) {
  const vm = this; const alive = paintable(vm, $scope); let sequence = 0;
  vm.filters = emptyFilters(); vm.groupBy = ['department'];
  vm.dimensions = [{ value: 'country', label: 'Country' }, { value: 'department', label: 'Department' }, { value: 'jobLevel', label: 'Job level' }];
  initReferences(vm, api);
  vm.dimensionLabel = key => vm.dimensions.find(item => item.value === key)?.label || key;
  vm.dimensionValue = (key, value) => key === 'country' ? vm.label('countries', value) : key === 'department' ? vm.label('departments', value) : value;
  vm.load = async () => {
    const current = ++sequence; vm.loading = true; vm.error = null; vm.report = null;
    try {
      const report = await api.get('/reports/compensation', { ...vm.filters, groupBy: vm.groupBy });
      if (alive() && current === sequence) { vm.report = report; vm.groupKeys = report.groupBy.filter(key => key !== 'currency'); }
    } catch (error) { if (alive() && current === sequence) vm.error = error; }
    finally { if (current === sequence) { vm.loading = false; vm.paint(); } }
  };
  vm.clear = () => { vm.filters = emptyFilters(); vm.groupBy = ['department']; vm.load(); };
  vm.groups = currency => (vm.report?.groups || []).filter(group => group.dimensions.currency === currency);
  vm.$onInit = () => { vm.loadReferences(); vm.load(); };
}
ReportsController.$inject = ['api', '$scope'];
const reportsTemplate = `
<section><div class="page-header"><div><h1>Compensation reports</h1><p>Explore annual base salaries across your organization.</p></div></div>
  ${referenceError}
  <form class="card filter-card" ng-submit="$ctrl.load()"><div class="filter-grid">${filterControls()}<button type="submit" class="btn btn-primary" ng-disabled="!$ctrl.refs">Apply filters</button></div><div class="filter-secondary"><div class="group-select"><label id="group-label">Group by</label><md-select ng-model="$ctrl.groupBy" multiple aria-label="Group by" placeholder="Currency only" ng-disabled="!$ctrl.refs"><md-option ng-repeat="dimension in $ctrl.dimensions" ng-value="dimension.value">{{dimension.label}}</md-option></md-select></div><span class="muted">Active employees only</span><button type="button" class="btn btn-secondary btn-small" ng-click="$ctrl.clear()" ng-disabled="!$ctrl.refs">Clear filters</button></div></form>
  ${loading}${requestError}
  <div ng-if="$ctrl.report && !$ctrl.loading">
    <div class="card empty-state" ng-if="!$ctrl.report.totalEmployees"><h2>No matching compensation data.</h2><p>Adjust your filters to include a different group of employees.</p><button class="btn btn-secondary" ng-click="$ctrl.clear()">Clear filters</button></div>
    <p class="muted" ng-if="$ctrl.report.currencySummaries.length > 1">{{$ctrl.report.totalEmployees | number}} matching active employees · {{$ctrl.report.currencySummaries.length}} currencies shown separately</p>
    <section class="currency-section" ng-repeat="summary in $ctrl.report.currencySummaries track by summary.currency" aria-label="{{summary.currency}} compensation">
      <div class="currency-label" ng-if="$ctrl.report.currencySummaries.length > 1"><h2>{{summary.currency}}</h2><small>{{$ctrl.label('currencies', summary.currency)}}</small></div>
      <div class="metrics"><div class="card metric"><span class="metric-label">Active employees</span><strong class="metric-value">{{summary.employeeCount | number}}</strong><small>Matching the selected filters</small></div><div class="card metric"><span class="metric-label">Total annual base salary</span><strong class="metric-value">{{summary.totalAnnualBaseSalary | formatMoney:summary.currency}}</strong><small>Total salary commitment</small></div><div class="card metric"><span class="metric-label">Average annual base salary</span><strong class="metric-value">{{summary.averageAnnualBaseSalary | formatMoney:summary.currency}}</strong><small>Mean per employee</small></div><div class="card metric"><span class="metric-label">Median annual base salary</span><strong class="metric-value">{{summary.medianAnnualBaseSalary | formatMoney:summary.currency}}</strong><small>Middle of the salary distribution</small></div></div>
      <div class="report-table-title"><h2>Salary breakdown</h2><small>{{summary.currency}} · Annual base salary</small></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th ng-repeat="key in $ctrl.groupKeys">{{$ctrl.dimensionLabel(key)}}</th><th ng-if="!$ctrl.groupKeys.length">Currency</th><th class="number">Employees</th><th class="number">Total annual base</th><th class="number">Average annual base</th><th class="number">Median annual base</th></tr></thead><tbody><tr ng-repeat="group in $ctrl.groups(summary.currency)"><td ng-repeat="key in $ctrl.groupKeys">{{$ctrl.dimensionValue(key, group.dimensions[key])}}</td><td ng-if="!$ctrl.groupKeys.length">{{summary.currency}}</td><td class="number">{{group.employeeCount | number}}</td><td class="number">{{group.totalAnnualBaseSalary | formatMoney:summary.currency}}</td><td class="number">{{group.averageAnnualBaseSalary | formatMoney:summary.currency}}</td><td class="number">{{group.medianAnnualBaseSalary | formatMoney:summary.currency}}</td></tr><tr class="report-total"><td ng-attr-colspan="{{$ctrl.groupKeys.length || 1}}">Total / overall</td><td class="number">{{summary.employeeCount | number}}</td><td class="number">{{summary.totalAnnualBaseSalary | formatMoney:summary.currency}}</td><td class="number">{{summary.averageAnnualBaseSalary | formatMoney:summary.currency}}</td><td class="number">{{summary.medianAnnualBaseSalary | formatMoney:summary.currency}}</td></tr></tbody></table></div>
    </section><div class="alert" ng-if="$ctrl.report.totalEmployees"><strong>Compare within the same currency</strong><p>All amounts are annual base salaries. Each currency is shown separately and is never combined with another.</p></div><p class="report-generated">Generated {{$ctrl.report.generatedAt | formatDateTime}}</p>
  </div>
</section>`;

export function registerCorePages(app) {
  app.value('directoryState', {});
  app.component('appShell', { controller: ShellController, template: shellTemplate });
  app.component('signIn', { controller: SignInController, template: signinTemplate });
  app.component('employeeDirectory', { controller: DirectoryController, template: directoryTemplate });
  app.component('compensationReports', { controller: ReportsController, template: reportsTemplate });
}

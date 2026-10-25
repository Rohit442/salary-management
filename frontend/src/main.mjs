import 'angular/angular';
import 'angular-route';
import 'angular-animate';
import 'angular-aria';
import 'angular-messages';
import 'angular-material';
import 'angular-material/angular-material.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import './styles/app.css';
import './styles/employees.css';
import { createApiClient } from './services/api.mjs';
import { formatMoney, formatDate, formatDateTime } from './util/format.mjs';
import { registerCorePages } from './components/core-pages.mjs';
import { registerEmployeePages } from './components/employee-pages.mjs';
import { registerCodepointMaxlength } from './validator/text-length.mjs';

const app = window.angular.module('acme', ['ngRoute', 'ngMaterial', 'ngMessages']);
app.config(['$sceDelegateProvider', function ($sceDelegateProvider) {
  // Views and Material dialogs are bundled inline; no URL-based templates are needed.
  // Reject dynamic resource URLs without regex matchers (CVE-2026-11998).
  // Never bypass this policy with user-derived trustAsResourceUrl or template-cache entries.
  $sceDelegateProvider.trustedResourceUrlList([]);
}]);
app.factory('api', ['$rootScope', '$location', function ($rootScope, $location) {
  return createApiClient(window.fetch.bind(window), error => $rootScope.$evalAsync(() => {
    $rootScope.accessMessage = $rootScope.sessionEstablished || error.status === 403 ? error.message : null;
    $rootScope.sessionEstablished = false;
    $location.path('/sign-in');
  }));
}]);
app.factory('sessionGate', ['api', '$q', function (api, $q) {
  let pending;
  const ready = () => pending || (pending = $q.when(api.restore()).catch(error => { pending = null; throw error; }));
  return { ready, require: () => ready().then(() => api.user || $q.reject({ code: 'SIGN_IN_REQUIRED' })),
    reset: () => { pending = null; } };
}]);
app.config(['$routeProvider', '$locationProvider', '$mdThemingProvider', function (routes, location, theme) {
  location.hashPrefix('!');
  theme.definePalette('acmeBlue', theme.extendPalette('indigo', { '500': '3c5ce8', '600': '2d49c7' }));
  theme.theme('default').primaryPalette('acmeBlue').accentPalette('blue');
  const protectedPage = template => ({ template, resolve: { identity: ['sessionGate', gate => gate.require()] } });
  routes.when('/sign-in', { template: '<sign-in></sign-in>', resolve: { identity: ['sessionGate', gate => gate.ready()] } })
    .when('/employees', protectedPage('<employee-directory></employee-directory>'))
    .when('/employees/new', protectedPage('<employee-form></employee-form>'))
    .when('/employees/:id/history', protectedPage('<employee-history></employee-history>'))
    .when('/employees/:id/salary', protectedPage('<employee-salary></employee-salary>'))
    .when('/employees/:id/edit', protectedPage('<employee-form></employee-form>'))
    .when('/employees/:id', protectedPage('<employee-detail></employee-detail>'))
    .when('/reports', protectedPage('<compensation-reports></compensation-reports>'))
    .otherwise({ redirectTo: '/employees' });
}]);
app.filter('formatMoney', () => formatMoney);
app.filter('formatDate', () => formatDate);
app.filter('formatDateTime', () => formatDateTime);
registerCodepointMaxlength(app);
registerCorePages(app);
registerEmployeePages(app);
window.angular.bootstrap(document, ['acme'], { strictDi: true });

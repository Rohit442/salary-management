export function registerCodepointMaxlength(app) {
  app.directive('codepointMaxlength', () => ({
    restrict: 'A',
    require: 'ngModel',
    link(_scope, _element, attributes, control) {
      const maximum = Number(attributes.codepointMaxlength);
      // Match API code-point limits while retaining significant password whitespace.
      control.$validators.codepointMaxlength = (_modelValue, viewValue) => {
        if (control.$isEmpty(viewValue)) return true;
        const value = String(viewValue);
        return Array.from(attributes.ngTrim === 'false' ? value : value.trim()).length <= maximum;
      };
    },
  }));
}

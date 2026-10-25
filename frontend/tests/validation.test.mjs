import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSalaryAmount, validateSalaryEffectiveDate } from '../src/validator/salary.mjs';
import { registerCodepointMaxlength } from '../src/validator/text-length.mjs';

test('salary validation enforces positive values, currency precision and the storage limit', () => {
  for (const value of ['', '0', '0.00', '-1', '1e6', '1,000', '1.001', '1000000000000000']) {
    assert.equal(typeof validateSalaryAmount(value), 'string', value);
  }
  for (const value of ['0.01', '999999999999999.99', '100']) assert.equal(validateSalaryAmount(value), null);
  assert.equal(validateSalaryAmount('1', 0), null);
  assert.equal(typeof validateSalaryAmount('1.0', 0), 'string');
  assert.equal(validateSalaryAmount('0.0001', 4), null);
  assert.equal(typeof validateSalaryAmount('0.00001', 4), 'string');
});

test('salary dates enforce the calendar and every inclusive employment boundary', () => {
  const employee = { joiningDate: '2024-02-29', salaryEffectiveFrom: '2024-02-28', lastEmploymentDate: '2024-03-31' };
  const validate = (date, record = employee) => validateSalaryEffectiveDate(date, record, '2024-03-31');
  for (const value of ['', '0000-01-01', '2024-2-29', '2023-02-29', '2024-04-31', '9999-99-99']) {
    assert.equal(typeof validate(value), 'string', value);
  }
  assert.match(validate('2024-04-01'), /on or before today/);
  assert.match(validate('2024-02-28'), /must be after/);
  assert.match(validate('2024-02-28', { ...employee, salaryEffectiveFrom: '2024-01-01' }), /joining date/);
  assert.match(validate('2024-03-31', { ...employee, lastEmploymentDate: '2024-03-30' }), /last employment date/);
  assert.equal(validate('2024-02-29'), null);
  assert.equal(validate('2024-03-31'), null);
  assert.equal(validate('2024-03-31', { ...employee, lastEmploymentDate: null }), null);
});

test('text limits count Unicode code points and retain significant password whitespace', () => {
  let definition;
  registerCodepointMaxlength({ directive(name, factory) {
    assert.equal(name, 'codepointMaxlength');
    definition = factory();
  } });
  function validator(ngTrim) {
    const model = { $validators: {}, $isEmpty: value => value == null || value === '' };
    definition.link(null, null, { codepointMaxlength: '2', ngTrim }, model);
    return value => model.$validators.codepointMaxlength(undefined, value);
  }
  const text = validator(undefined);
  for (const value of [undefined, null, '', 'ab', '  ab  ', '😀😀']) assert.equal(text(value), true);
  for (const value of ['abc', '😀😀😀']) assert.equal(text(value), false);
  assert.equal(validator('false')(' ab '), false);
  assert.equal(validator('false')(' a'), true);
});

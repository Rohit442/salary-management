import { formatDate } from '../util/format.mjs';

export function validateSalaryAmount(value, precision = 2) {
  if (!value) return 'Enter an annual base salary.';
  const fraction = precision ? `(\\.[0-9]{1,${precision}})?` : '';
  if (!new RegExp(`^[0-9]+${fraction}$`).test(value)) {
    return `Enter a positive decimal amount without commas, with up to ${precision} decimal places.`;
  }
  if (value.split('.')[0].length > 15) return 'Use no more than 15 digits before the decimal point.';
  if (!/[1-9]/.test(value)) return 'Enter a salary greater than zero.';
  return null;
}

export function validateSalaryEffectiveDate(value, employee, today) {
  if (!value) return 'Enter an effective date.';
  const date = new Date(`${value}T00:00:00Z`);
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.startsWith('0000')
      || Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    return 'Enter a valid date in YYYY-MM-DD format.';
  }
  if (value > today) return `Effective date must be on or before today (${formatDate(today)}).`;
  if (value <= employee.salaryEffectiveFrom) return `Effective date must be after ${formatDate(employee.salaryEffectiveFrom)}.`;
  if (value < employee.joiningDate) return `Effective date must be on or after the joining date (${formatDate(employee.joiningDate)}).`;
  if (employee.lastEmploymentDate && value > employee.lastEmploymentDate) {
    return `Effective date must be on or before the last employment date (${formatDate(employee.lastEmploymentDate)}).`;
  }
  return null;
}

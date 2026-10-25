const UNAVAILABLE = '—';
const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** Group decimal text directly: salary/report totals must never pass through floating point. */
export function formatMoney(value, currency = '', minimumFractionDigits = 2) {
  if (typeof value !== 'string' || !/^-?\d+(?:\.\d+)?$/.test(value)) return UNAVAILABLE;
  if (!Number.isInteger(minimumFractionDigits) || minimumFractionDigits < 0 || minimumFractionDigits > 20) return UNAVAILABLE;
  const [integer, fraction = ''] = value.split('.');
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const decimal = fraction.padEnd(minimumFractionDigits, '0');
  return `${currency ? `${currency} ` : ''}${grouped}${decimal ? `.${decimal}` : ''}`;
}

function calendarDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  return year > 0 && date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day ? date : null;
}

export function formatDate(value) {
  const date = calendarDate(value);
  return date ? `${String(date.getUTCDate()).padStart(2, '0')} ${months[date.getUTCMonth()].slice(0, 3)} ${String(date.getUTCFullYear()).padStart(4, '0')}` : UNAVAILABLE;
}

export function formatDateTime(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)
      || !calendarDate(value.slice(0, 10))) return UNAVAILABLE;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return UNAVAILABLE;
  return `${formatDate(date.toISOString().slice(0, 10))}, ${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')} UTC`;
}

export function formatMonth(value) {
  const date = typeof value === 'string' ? calendarDate(`${value}-01`) : null;
  return date ? `${months[date.getUTCMonth()]} ${String(date.getUTCFullYear()).padStart(4, '0')}` : UNAVAILABLE;
}

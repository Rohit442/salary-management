import test from 'node:test';
import assert from 'node:assert/strict';
import { formatMoney, formatDate, formatDateTime, formatMonth } from '../src/util/format.mjs';

test('money grouping preserves every digit above JavaScript numeric precision', () => {
  assert.equal(formatMoney('99999999999999999999.99', 'INR'), 'INR 99,999,999,999,999,999,999.99');
  assert.equal(formatMoney('1320000.00', 'INR'), 'INR 1,320,000.00');
});

test('money formatting pads minimum decimals without rounding or truncating', () => {
  assert.equal(formatMoney('1200', 'USD'), 'USD 1,200.00');
  assert.equal(formatMoney('1200.1', 'USD'), 'USD 1,200.10');
  assert.equal(formatMoney('1200.1234', 'USD'), 'USD 1,200.1234');
  assert.equal(formatMoney('0.00', 'INR'), 'INR 0.00');
  assert.equal(formatMoney('12', 'JPY', 0), 'JPY 12');
});

test('invalid money is unavailable rather than a fabricated numeric amount', () => {
  for (const value of [null, undefined, 1200, '', '1e6', '1,200', 'NaN']) assert.equal(formatMoney(value, 'INR'), '—');
});

test('calendar dates retain their date without timezone conversion', () => {
  assert.equal(formatDate('2026-10-15'), '15 Oct 2026');
  assert.equal(formatDate('2024-02-29'), '29 Feb 2024');
  assert.equal(formatDate('2025-02-29'), '—');
  assert.equal(formatDate(null), '—');
});

test('recording timestamps explicitly show UTC and convert supplied offsets', () => {
  assert.equal(formatDateTime('2026-10-25T05:30:00Z'), '25 Oct 2026, 05:30 UTC');
  assert.equal(formatDateTime('2026-10-25T11:00:00+05:30'), '25 Oct 2026, 05:30 UTC');
  assert.equal(formatDateTime('2026-10-25T05:30:00'), '—');
  assert.equal(formatDateTime('invalid'), '—');
});

test('month labels preserve the requested calendar month', () => {
  assert.equal(formatMonth('2026-11'), 'November 2026');
  assert.equal(formatMonth('2026-13'), '—');
});

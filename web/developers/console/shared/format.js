/* Sankalp console — money and token formatting.
 *
 * Everything here takes the stored shape and produces what a person reads. The stored
 * shape is an integer of paise and an integer of tokens; the readable shape is "₹375.50"
 * and "482k". Keeping the two apart is what stops a float from ever touching a balance.
 */
(function () {
  'use strict';

  /** 37550 -> "₹375.50"   ·   50000 -> "₹500.00"   ·   0 -> "₹0.00" */
  function formatPaise(paise) {
    const n = Number(paise);
    if (!isFinite(n)) return '₹0.00';
    const sign = n < 0 ? '-' : '';
    const abs = Math.abs(Math.round(n));
    const rupees = Math.floor(abs / 100);
    const rest = String(abs % 100).padStart(2, '0');
    return `${sign}₹${rupees.toLocaleString('en-IN')}.${rest}`;
  }

  /** 37550 -> "₹375.50" without the decimals when they are zero: 50000 -> "₹500" */
  function formatPaiseShort(paise) {
    const text = formatPaise(paise);
    return text.endsWith('.00') ? text.slice(0, -3) : text;
  }

  /** 482000 -> "482k"   ·   1240000 -> "1.24M"   ·   940 -> "940" */
  function formatTokens(count) {
    const n = Number(count);
    if (!isFinite(n)) return '0';
    if (n >= 1000000) return `${(n / 1000000).toFixed(2).replace(/\.?0+$/, '')}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`;
    return String(Math.round(n));
  }

  /** 1240 -> "1,240" */
  function formatCount(count) {
    const n = Number(count);
    return isFinite(n) ? Math.round(n).toLocaleString('en-IN') : '0';
  }

  /** epoch ms -> "18 Nov 2026, 9:14 pm"  ·  null -> "never" */
  function formatWhen(ms) {
    const n = Number(ms);
    if (!n || !isFinite(n)) return 'never';
    const d = new Date(n);
    const day = d.getDate();
    const month = d.toLocaleString('en-IN', { month: 'short' });
    const year = d.getFullYear();
    let hour = d.getHours();
    const minute = String(d.getMinutes()).padStart(2, '0');
    const ampm = hour >= 12 ? 'pm' : 'am';
    hour = hour % 12 || 12;
    return `${day} ${month} ${year}, ${hour}:${minute} ${ampm}`;
  }

  /** epoch ms -> "18 Nov" (the wallet ledger wants a short date) */
  function formatDay(ms) {
    const n = Number(ms);
    if (!n || !isFinite(n)) return '—';
    const d = new Date(n);
    return `${d.getDate()} ${d.toLocaleString('en-IN', { month: 'short' })}`;
  }

  /** "2026-11" -> "November 2026" */
  function formatMonth(key) {
    const m = /^(\d{4})-(\d{2})$/.exec(String(key || ''));
    if (!m) return String(key || '');
    const d = new Date(Number(m[1]), Number(m[2]) - 1, 1);
    return d.toLocaleString('en-IN', { month: 'long', year: 'numeric' });
  }

  /** The current month key in IST, e.g. "2026-11". */
  function currentMonthKey() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  window.SankalpFormat = {
    formatPaise,
    formatPaiseShort,
    formatTokens,
    formatCount,
    formatWhen,
    formatDay,
    formatMonth,
    currentMonthKey,
  };
})();

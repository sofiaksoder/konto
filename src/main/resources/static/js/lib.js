// Shared helpers: DOM builder, money formatting, API client.

/** Builds an element. Children are text-safe (never parsed as HTML). */
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'style' && typeof value === 'object') Object.assign(el.style, value);
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2), value);
    else if (key in el && key !== 'list' && key !== 'form') el[key] = value;
    else el.setAttribute(key, value === true ? '' : value);
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

export function svg(tag, attrs = {}, ...children) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
  append(el, children);
  return el;
}

export function clear(el) {
  el.replaceChildren();
  return el;
}

/** Politely announces a message to screen readers. */
export function announce(message) {
  const live = document.getElementById('live');
  live.textContent = '';
  setTimeout(() => { live.textContent = message; }, 30);
}

// ---------- money ----------

export const CURRENCIES = ['USD', 'EUR', 'GBP', 'SEK', 'JPY', 'CHF'];
export const MINUS = '−';

export const decimals = (currency) => (currency === 'JPY' ? 0 : 2);

const formatters = new Map();
function formatter(min, max) {
  const key = `${min}:${max}`;
  if (!formatters.has(key)) {
    formatters.set(key, new Intl.NumberFormat('en-US', { minimumFractionDigits: min, maximumFractionDigits: max }));
  }
  return formatters.get(key);
}

/** 2 decimals (0 for JPY), tabular grouping, no currency code. */
export function money(value, currency = 'USD') {
  const d = decimals(currency);
  return formatter(d, d).format(Math.abs(Number(value)) < 0.5 * 10 ** -d ? 0 : value);
}

/** Money with an explicit sign: +92.00 or −100.00 (real minus sign). */
export function signed(value, currency, direction) {
  return `${direction === 'DEBIT' ? MINUS : '+'}${money(Math.abs(value), currency)}`;
}

/** Full 4 decimal value for tooltips, e.g. "1,284,903,221.5000 JPY". */
export function full(value, currency) {
  return `${formatter(4, 4).format(value)} ${currency}`;
}

export const plain = (value, min = 0, max = 4) => formatter(min, max).format(value);
export const accountLabel = (id) => `A-${String(id).padStart(4, '0')}`;
export const transferLabel = (id) => `T-${String(id).padStart(5, '0')}`;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const pad2 = (n) => String(n).padStart(2, '0');
export function shortDate(iso) {
  const d = new Date(iso);
  return `${d.getDate()} ${MONTHS[d.getMonth()]}`;
}
export function longDate(iso) {
  const d = new Date(iso);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}, ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** Parses "1,250.50" style input; returns NaN when it is not a plain number. */
export function parseNumber(text) {
  const cleaned = String(text).replace(/[\s,]/g, '');
  return /^-?\d*\.?\d+$|^-?\d+\.$/.test(cleaned) ? Number(cleaned) : NaN;
}

// ---------- API ----------

export class ApiError extends Error {
  constructor(status, detail) {
    super(detail || `Request failed (${status})`);
    this.status = status;
  }
}

export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  let res;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'We could not reach the server.');
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const fieldErrors = Array.isArray(data?.errors) ? data.errors.map((e) => e.defaultMessage).join('; ') : '';
    throw new ApiError(res.status, data?.detail || fieldErrors || res.statusText);
  }
  return data;
}

// ---------- shared UI pieces ----------

export function field(label, control, { hint, error, id } = {}) {
  const inputId = id || control.querySelector?.('input, select')?.id;
  return h('div', { class: 'field' },
    h('label', { for: inputId }, label),
    control,
    error ? h('div', { class: 'field-error', id: `${inputId}-error`, role: 'alert' }, `! ${error}`) : null,
    hint ? h('div', { class: 'field-hint' }, hint) : null);
}

/** A bordered input with an optional unit on the right. */
export function inputBox(id, { value = '', unit = '', type = 'text', inputmode = 'decimal', invalid = false, ...rest } = {}) {
  return h('div', { class: `input-wrap${invalid ? ' invalid' : ''}` },
    h('input', { id, type, value, inputMode: inputmode, autocomplete: 'off', 'aria-invalid': invalid ? 'true' : null, ...rest }),
    unit ? h('span', { class: 'unit' }, unit) : null);
}

export function selectBox(id, options, selected) {
  return h('div', { class: 'input-wrap' },
    h('select', { id }, options.map((o) => h('option', { value: o, selected: o === selected }, o))),
    h('span', { class: 'unit', 'aria-hidden': 'true' }, '▾'));
}

export function segmented(label, options, selected, onChange) {
  const wrap = h('div', { class: 'segmented', role: 'group', 'aria-label': label });
  const buttons = options.map(([value, text]) => h('button', {
    type: 'button', 'aria-pressed': String(value === selected),
    onclick: () => { buttons.forEach((b) => b.setAttribute('aria-pressed', 'false')); buttons.find((b) => b.dataset.value === value).setAttribute('aria-pressed', 'true'); onChange(value); },
    'data-value': value,
  }, text));
  wrap.append(...buttons);
  return wrap;
}

export function skeleton(width, height = 12) {
  return h('div', { class: 'skeleton', style: { width, height: `${height}px` }, 'aria-hidden': 'true' });
}

/** Thin ridge-line motif used in empty states. */
export function ridge() {
  return svg('svg', { width: '100%', height: 70, viewBox: '0 0 200 70', fill: 'none', stroke: '#9FB3C4', 'stroke-width': '1.4', 'aria-hidden': 'true' },
    svg('polyline', { points: '0,64 50,30 70,46 110,8 160,58 200,40' }));
}

export function errorPanel(message, onRetry) {
  return h('div', { class: 'callout warn', role: 'alert' },
    h('span', { class: 'mark' }, '!'),
    h('span', {}, message, onRetry ? [' ', h('a', { href: '#', onclick: (e) => { e.preventDefault(); onRetry(); } }, 'Try again')] : null));
}

export function notFound(message = 'We couldn’t find that page.') {
  return h('div', { class: 'page' },
    h('div', { class: 'empty' }, ridge(), h('h1', { class: 'page-title' }, message), h('a', { class: 'btn btn-primary', href: '#/accounts' }, 'Go home')));
}

/** Slim photo banner shared by the Money pages (Accounts, statements, Send money). */
export function banner(title) {
  return h('header', { class: 'banner' }, h('h1', {}, title));
}

export function pageHeader(label, title, sub) {
  return h('div', {},
    label ? h('div', { class: 'label-mono', style: { color: '#1B4A73' } }, label) : null,
    h('h1', { class: 'page-title' }, title),
    sub ? h('p', { class: 'page-sub' }, sub) : null);
}

export const tile = (label, value, desc) => h('div', { class: 'tile' },
  h('div', { class: 'label-mono' }, label), h('div', { class: 'value' }, value), h('div', { class: 'desc' }, desc));

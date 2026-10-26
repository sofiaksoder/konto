// Shared two-column layout for the calculator screens: inputs on the left, result on the right.
import { h, clear, parseNumber, plain, inputBox, errorPanel, announce } from './lib.js';

/** A labelled numeric field with inline error support. */
export function numField(id, label, { value = '', unit = '', hint = '', optional = false, group = false } = {}) {
  const box = inputBox(id, { value: String(value), unit, 'aria-describedby': `${id}-msg` });
  const input = box.querySelector('input');
  if (group) {
    input.addEventListener('blur', () => {
      const v = parseNumber(input.value);
      if (!Number.isNaN(v)) input.value = plain(v, 0, 4);
    });
  }
  const msg = h('div', { id: `${id}-msg`, role: 'alert' });
  const hintEl = hint ? h('div', { class: 'field-hint' }, hint) : null;
  return {
    el: h('div', { class: 'field' }, h('label', { for: id }, label), box, msg, hintEl),
    input,
    raw: () => input.value.trim(),
    /** Parsed number, or NaN. Empty optional fields give null. */
    value() {
      const t = input.value.trim();
      if (optional && (t === '' || t === '—')) return null;
      return parseNumber(t);
    },
    error(text) {
      clear(msg);
      box.classList.toggle('invalid', Boolean(text));
      input.setAttribute('aria-invalid', text ? 'true' : 'false');
      if (text) msg.append(h('div', { class: 'field-error' }, `! ${text}`));
    },
  };
}

/** Checks each field with a rule; shows messages and returns true when all pass. */
export function check(rules) {
  let ok = true;
  let first = null;
  for (const [field, test, message] of rules) {
    const v = field.value();
    const bad = v === null ? false : !test(v);
    field.error(bad ? message : '');
    if (bad) { ok = false; first ??= field; }
  }
  first?.input.focus();
  return ok;
}

export function toolPage({ title, intro, fields, submitLabel, onSubmit, extra = [] }) {
  const result = h('div', { class: 'tool-result', 'aria-live': 'polite' });
  const form = h('form', { novalidate: true, class: 'tool-inputs', onsubmit: async (e) => {
    e.preventDefault();
    await onSubmit(result);
  } },
    h('h1', {}, title),
    intro ? h('p', { class: 'field-hint', style: { marginTop: '-6px' } }, intro) : null,
    fields.map((f) => f.el ?? f),
    extra,
    h('div', { class: 'submit' }, h('button', { class: 'btn btn-primary', type: 'submit' }, submitLabel)));
  return { root: h('div', { class: 'tool' }, form, result), result, submit: () => onSubmit(result) };
}

/** Runs a request, showing a plain-language error panel in `result` when it fails. */
export async function run(result, work, friendly = (e) => e.message) {
  try {
    const node = await work();
    clear(result).append(node);
    announce('Result updated');
  } catch (e) {
    clear(result).append(errorPanel(friendly(e)));
  }
}

export function placeholder(text) {
  return h('div', { class: 'callout plain', style: { maxWidth: '520px' } }, text);
}

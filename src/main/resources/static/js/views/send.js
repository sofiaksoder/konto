import { h, banner, api, ApiError, clear, money, full, plain, accountLabel, transferLabel, longDate, parseNumber, errorPanel, announce, MINUS } from '../lib.js';

const STEPS = ['Details', 'Confirm', 'Sending', 'Result'];

export async function render(query) {
  const root = h('div', { class: 'page' });
  const view = h('div', {}, banner('Send money'), root);
  let accounts;
  try {
    accounts = await api('/accounts');
  } catch (e) {
    root.append(errorPanel(`We couldn’t load your accounts. ${e.message}`));
    return view;
  }
  if (accounts.length < 2) {
    root.append(needsTwoAccounts(accounts));
    return view;
  }

  const byId = (id) => accounts.find((a) => a.id === Number(id));
  const state = {
    step: 1,
    fromId: byId(query.get('from')) ? Number(query.get('from')) : accounts[0].id,
    toId: null,
    amountText: '',
    touched: false,
    rate: null,        // { rate, from, to }
    key: null,         // idempotency key, created on entering step 2 and reused for every retry
    error: null,       // { message, statementLink? }
    result: null,
  };
  state.toId = otherAccounts()[0].id;

  function otherAccounts() { return accounts.filter((a) => a.id !== state.fromId); }
  const from = () => byId(state.fromId);
  const to = () => byId(state.toId);
  const amount = () => parseNumber(state.amountText);

  /** Returns a plain-language problem with the amount, or null. */
  function amountProblem() {
    const text = state.amountText.replace(/,/g, '').trim();
    if (text === '' ) return { message: 'Enter an amount to send', soft: true };
    const n = amount();
    if (Number.isNaN(n)) return { message: 'Use numbers only, for example 100.00' };
    if (!/^\d*\.?\d{0,4}$/.test(text)) return { message: 'Use at most 4 decimal places' };
    if (n <= 0) return { message: `Enter more than ${money(0, from().currency)}` };
    if (n > from().balance) {
      return { message: `This account doesn’t have enough to send ${money(n, from().currency)} ${from().currency}. Available: ${money(from().balance, from().currency)} ${from().currency}.`, funds: true };
    }
    return null;
  }

  async function loadRate() {
    state.rate = null;
    if (from().currency === to().currency) return;
    const { currency: a } = from();
    const { currency: b } = to();
    try {
      const r = await api(`/fx/rate?from=${a}&to=${b}`);
      if (from().currency === a && to().currency === b) { state.rate = r; if (state.step === 1) drawPreview(); }
    } catch { /* preview just stays hidden; the transfer itself still works */ }
  }

  const stepper = () => h('ol', { class: 'stepper', 'aria-label': 'Progress' },
    STEPS.map((label, i) => h('li', { class: `${i < state.step ? 'done' : ''} ${i === state.step - 1 ? 'current' : ''}`, 'aria-current': i === state.step - 1 ? 'step' : null }, `${i + 1} ${label}`)));

  function draw() {
    clear(root).append(stepper(), [stepDetails, stepConfirm, stepSending, stepResult][state.step - 1]());
    if (state.step === 1) drawPreview();
  }

  // ---------- step 1 ----------
  let previewEl; let errorEl; let reviewBtn; let amountBox; let fromAvail;

  function picker(label, id, list, selected, onChange, availText) {
    const select = h('select', { id, onchange: (e) => onChange(Number(e.target.value)) },
      list.map((a) => h('option', { value: a.id, selected: a.id === selected }, `${accountLabel(a.id)} · ${a.owner}`)));
    const avail = h('div', { class: 'avail' }, availText());
    return { el: h('div', { class: 'picker' }, h('label', { class: 'label-mono', for: id }, label), select, avail), avail };
  }

  function stepDetails() {
    const fromPicker = picker('From', 'send-from', accounts, state.fromId, async (id) => {
      state.fromId = id;
      if (state.toId === id) state.toId = otherAccounts()[0].id;
      await loadRate();
      draw();
      document.getElementById('send-from').focus();
    }, () => `${money(from().balance, from().currency)} ${from().currency} available`);
    fromAvail = fromPicker.avail;

    const toPicker = picker('To (you can’t pick the same account)', 'send-to', otherAccounts(), state.toId, async (id) => {
      state.toId = id;
      await loadRate();
      draw();
      document.getElementById('send-to').focus();
    }, () => `${money(to().balance, to().currency)} ${to().currency} balance`);

    const input = h('input', { id: 'send-amount', inputMode: 'decimal', autocomplete: 'off', placeholder: '0.00', value: state.amountText, 'aria-describedby': 'send-error send-preview',
      'aria-label': 'Amount you send', oninput: (e) => { state.amountText = e.target.value; update(); } });
    amountBox = h('div', { class: 'amount-box' },
      h('div', { style: { flex: 1, minWidth: 0 } }, h('label', { class: 'label-mono', for: 'send-amount' }, 'You send'), h('div', {}, input)),
      h('div', { class: 'cur' }, from().currency));

    errorEl = h('div', { id: 'send-error', role: 'alert' });
    previewEl = h('div', { id: 'send-preview', 'aria-live': 'polite' });
    reviewBtn = h('button', { class: 'btn btn-primary', type: 'button', onclick: review }, 'Review transfer →');

    queueMicrotask(() => { update(); });
    return h('div', { class: 'stack' },
      h('p', { class: 'page-sub' }, 'Transfers can’t be undone, so you’ll review everything first.'),
      h('div', { class: 'two' }, fromPicker.el, toPicker.el),
      amountBox, errorEl, previewEl,
      h('div', { class: 'actions' }, reviewBtn, h('a', { class: 'btn btn-secondary', href: '#/accounts' }, 'Cancel')));
  }

  function update() {
    const problem = amountProblem();
    const show = problem && (!problem.soft || state.touched) && state.amountText !== '';
    amountBox.classList.toggle('invalid', Boolean(show));
    clear(errorEl);
    if (show) {
      errorEl.append(h('div', { class: 'callout warn' }, h('span', { class: 'mark' }, '!'), h('span', {}, problem.message)));
    } else if (problem && problem.soft && state.touched) {
      errorEl.append(h('div', { class: 'field-error' }, `! ${problem.message}`));
    }
    reviewBtn.disabled = Boolean(problem);
    drawPreview();
  }

  function drawPreview() {
    if (!previewEl) return;
    clear(previewEl);
    const n = amount();
    if (amountProblem() || Number.isNaN(n)) return;
    const f = from().currency; const t = to().currency;
    if (f === t) {
      previewEl.append(h('div', { class: 'callout info num' }, 'You send ', h('b', {}, `${money(n, f)} ${f}`), ', they receive ', h('b', {}, `${money(n, t)} ${t}`)));
    } else if (state.rate) {
      const got = n * Number(state.rate.rate);
      previewEl.append(h('div', { class: 'callout info num' },
        'You send ', h('b', {}, `${money(n, f)} ${f}`), ', they receive about ', h('b', {}, `${money(got, t)} ${t}`), ` at ${plain(Number(state.rate.rate), 2, 4)}`,
        h('small', {}, 'This is an estimate. You’ll see the exact figures once it’s sent.')));
    }
  }

  function review() {
    state.touched = true;
    if (amountProblem()) { update(); document.getElementById('send-amount').focus(); return; }
    state.key = crypto.randomUUID();
    state.error = null;
    state.step = 2;
    draw();
    announce('Check the details before you send');
  }

  // ---------- step 2 ----------
  function stepConfirm() {
    const n = amount(); const f = from().currency; const t = to().currency;
    const got = f === t ? n : n * Number(state.rate?.rate ?? NaN);
    const rows = [
      ['From', `${accountLabel(from().id)} · ${from().owner}`],
      ['To', `${accountLabel(to().id)} · ${to().owner}`],
    ];
    if (f !== t && state.rate) rows.push(['Rate (estimate)', `1 ${f} = ${plain(Number(state.rate.rate), 2, 4)} ${t}`]);

    return h('div', { class: 'stack' },
      h('h2', { class: 'step-title' }, 'Check before you send'),
      h('div', { class: 'summary' },
        h('div', { class: 'big' }, h('div', { class: 'label-mono' }, 'Leaves your account'),
          h('div', { class: 'v', title: full(n, f) }, `${MINUS}${money(n, f)} `, h('span', { class: 'cur' }, f))),
        Number.isNaN(got) ? null : h('div', { class: 'big' }, h('div', { class: 'label-mono' }, f === t ? 'Arrives' : 'Arrives, about'),
          h('div', { class: 'v' }, `+${money(got, t)} `, h('span', { class: 'cur' }, t))),
        rows.map(([k, v]) => h('div', { class: 'kv' }, h('span', {}, k), h('span', { class: 'long-number' }, v)))),
      state.error ? errorBlock() : null,
      h('div', { class: 'callout warn' }, h('span', { class: 'mark' }, '!'), h('span', {}, h('b', {}, 'This can’t be undone.'), ' Once sent, there’s no cancel or refund.')),
      h('div', { class: 'actions' },
        h('button', { class: 'btn btn-primary', type: 'button', onclick: submit }, state.error?.retry ? `Try again: send ${money(n, f)} ${f}` : `Send ${money(n, f)} ${f}`),
        h('button', { class: 'btn btn-secondary', type: 'button', onclick: () => { state.step = 1; state.error = null; draw(); } }, '← Edit')));
  }

  function errorBlock() {
    return h('div', { class: 'callout warn', role: 'alert' }, h('span', { class: 'mark' }, '!'),
      h('span', {}, state.error.message, state.error.statementLink ? [' ', h('a', { href: `#/accounts/${state.fromId}` }, 'Check statement')] : null));
  }

  // ---------- step 3 ----------
  async function submit() {
    const n = amount(); const f = from().currency;
    state.step = 3;
    state.error = null;
    draw();
    announce(`Sending ${money(n, f)} ${f}`);
    try {
      state.result = await api('/transfers', {
        method: 'POST',
        headers: { 'Idempotency-Key': state.key },   // same key on every retry, so the money moves once
        body: { fromAccountId: state.fromId, toAccountId: state.toId, amount: n },
      });
      state.step = 4;
      draw();
      announce(`Sent. ${money(state.result.amount, f)} ${f} left your account.`);
      accounts = await api('/accounts').catch(() => accounts);
    } catch (e) {
      await failed(e);
    }
  }

  async function failed(e) {
    const n = amount(); const f = from().currency;
    if (e instanceof ApiError && e.status === 422) {
      accounts = await api('/accounts').catch(() => accounts);
      state.error = { message: `This account doesn’t have enough to send ${money(n, f)} ${f}. Available: ${money(from().balance, f)} ${f}.` };
    } else if (e instanceof ApiError && e.status === 409) {
      state.error = { message: 'This looks like a repeat of an earlier transfer.', statementLink: true };
    } else if (e instanceof ApiError && e.status === 0) {
      state.error = { message: 'We couldn’t confirm whether this went through. It’s safe to try again: the transfer is protected so the money moves only once.', retry: true };
    } else {
      state.error = { message: e.message || 'Something went wrong.', retry: true };
    }
    state.step = 2;
    draw();
    announce(state.error.message);
  }

  function stepSending() {
    const n = amount(); const f = from().currency;
    return h('div', { class: 'stack' },
      h('h2', { class: 'step-title', style: { marginTop: '24px' } }, `Sending ${money(n, f)} ${f}…`),
      h('div', { class: 'progress', 'aria-hidden': 'true' }, h('div')),
      h('div', { class: 'checklist' }, h('div', {}, '✓ Request sent'), h('div', { class: 'pending' }, '○ Moving the money'), h('div', { class: 'pending' }, '○ Recording it in both statements')),
      h('div', { class: 'callout info', style: { maxWidth: '560px' } }, h('b', {}, 'Taking longer than usual?'),
        ' Please don’t send again or reload. We’ve protected this transfer so the money moves only once, even if your connection drops.'),
      h('div', {}, h('button', { class: 'btn', type: 'button', disabled: true }, 'Sending…')));
  }

  // ---------- step 4 ----------
  function stepResult() {
    const r = state.result;
    const f = from().currency; const t = to().currency;
    return h('div', { class: 'stack' },
      h('div', { class: 'success-head' },
        h('div', { class: 'check', 'aria-hidden': 'true' }, '✓'),
        h('div', {}, h('h2', { class: 'step-title' }, 'Sent. It went through.'),
          h('div', { class: 'page-sub' }, `Transfer ${transferLabel(r.id)} · ${longDate(r.createdAt)}`))),
      h('div', { class: 'two num' },
        h('div', { class: 'card', title: full(r.amount, f) }, h('div', { class: 'label-mono' }, `Debited · ${accountLabel(r.fromAccountId)}`),
          h('div', { class: 'summary', style: { border: 0 } }, h('div', { class: 'big', style: { padding: '8px 0 0', background: 'none' } }, h('div', { class: 'v' }, `${MINUS}${money(r.amount, f)} `, h('span', { class: 'cur' }, f))))),
        h('div', { class: 'card', title: full(r.creditedAmount, t) }, h('div', { class: 'label-mono' }, `Credited · ${accountLabel(r.toAccountId)}`),
          h('div', { class: 'summary', style: { border: 0 } }, h('div', { class: 'big', style: { padding: '8px 0 0', background: 'none' } }, h('div', { class: 'v' }, `+${money(r.creditedAmount, t)} `, h('span', { class: 'cur' }, t)))))),
      f === t ? null : h('div', { class: 'callout plain num' }, 'Exchange rate used: ', h('b', {}, `1 ${f} = ${plain(Number(r.fxRate), 4, 4)} ${t}`)),
      h('div', { class: 'actions' },
        h('a', { class: 'btn btn-primary', href: `#/accounts/${r.fromAccountId}` }, 'View statement →'),
        h('button', { class: 'btn btn-secondary', type: 'button', onclick: async () => {
          state.step = 1; state.amountText = ''; state.touched = false; state.result = null; state.key = null;
          if (to().id === from().id) state.toId = otherAccounts()[0].id;
          await loadRate(); draw();
        } }, 'Send another')));
  }

  await loadRate();
  draw();
  return view;
}

/** Shown until there are two accounts: a transfer always goes from one Konto account to another. */
function needsTwoAccounts(accounts) {
  const has = accounts.length;
  return h('div', { class: 'stack' },
    h('p', { class: 'page-sub' }, 'Transfers move money from one Konto account to another.'),
    h('section', { class: 'notice-card', 'aria-labelledby': 'two-accounts-title' },
      h('div', { class: 'label-mono', id: 'two-accounts-title' }, has === 0 ? 'Two accounts needed' : 'One more account needed'),
      h('p', {}, has === 0
        ? 'You don\u2019t have any accounts yet. Open two, one to send from and one to receive, and you can move money between them.'
        : 'You have one account so far, so there\u2019s nowhere to send money yet. Open a second account, in any currency, to send to it.'),
      accounts.map((a) => h('div', { class: 'mini-account' },
        h('span', {}, h('span', { class: 'label-mono' }, accountLabel(a.id)), ' ', a.owner),
        h('span', { class: 'num', title: full(a.balance, a.currency) }, `${money(a.balance, a.currency)} ${a.currency}`))),
      h('div', {}, h('a', { class: 'btn btn-primary', href: '#/accounts?open=1&then=send' },
        has === 0 ? 'Open an account' : 'Open a second account'))));
}

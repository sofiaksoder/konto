import { h, api, clear, money, full, accountLabel, parseNumber, field, inputBox, selectBox, ridge, skeleton, errorPanel, CURRENCIES, announce } from '../lib.js';

export function render() {
  const grid = h('div', { class: 'account-grid', 'aria-busy': 'true' });
  const dialog = openDialog(load);
  const openBtn = h('button', { class: 'btn btn-primary hero-action', type: 'button', onclick: () => dialog.showModal() }, '+ Open account');

  const view = h('div', {},
    h('div', { class: 'hero' },
      h('div', { class: 'hero-text' }, h('div', { class: 'label-mono' }, 'Money'), h('h1', {}, 'Accounts')),
      openBtn),
    grid, dialog);

  async function load() {
    clear(grid).append(...[0, 1, 2].map(skeletonCard));
    grid.setAttribute('aria-busy', 'true');
    try {
      const accounts = await api('/accounts');
      clear(grid);
      if (accounts.length === 0) {
        grid.append(emptyState(() => dialog.showModal()));
      } else {
        grid.append(...accounts.map(accountCard), h('button', { class: 'new-account', type: 'button', onclick: () => dialog.showModal() }, '+ New account'));
      }
      announce(`${accounts.length} accounts`);
    } catch (e) {
      clear(grid).append(errorPanel(`We couldn’t load your accounts. ${e.message}`, load));
    }
    grid.setAttribute('aria-busy', 'false');
  }
  load();
  return view;
}

function accountCard(a) {
  return h('article', { class: 'card account-card' },
    h('div', { class: 'top' }, h('span', {}, accountLabel(a.id)), h('span', { class: 'pill' }, a.currency)),
    h('div', { class: 'balance', title: full(a.balance, a.currency), tabindex: '0', 'aria-label': `Balance ${money(a.balance, a.currency)} ${a.currency}` },
      money(a.balance, a.currency), ' ', h('span', { class: 'cur' }, a.currency)),
    h('div', { class: 'bottom' },
      h('span', { class: 'long-number' }, a.owner),
      h('a', { href: `#/accounts/${a.id}` }, 'Statement →')));
}

function skeletonCard() {
  return h('div', { class: 'card', 'aria-hidden': 'true', style: { display: 'flex', flexDirection: 'column', gap: '14px' } },
    skeleton('40%'), skeleton('80%', 34), skeleton('60%'));
}

function emptyState(onOpen) {
  return h('div', { class: 'empty', style: { gridColumn: '1 / -1' } },
    ridge(),
    h('h2', {}, 'No accounts yet'),
    h('p', {}, 'Open your first account to start sending money.'),
    h('button', { class: 'btn btn-primary btn-small', type: 'button', onclick: onOpen }, 'Open account'));
}

function openDialog(onCreated) {
  const dialog = h('dialog', { 'aria-labelledby': 'open-title' });
  const error = h('div', { role: 'alert' });

  function build() {
    const owner = inputBox('open-owner', { inputmode: 'text', required: true, maxLength: 80, autocomplete: 'name' });
    const currency = selectBox('open-currency', CURRENCIES, 'USD');
    const deposit = inputBox('open-deposit', { value: '0', unit: 'USD' });
    const ownerErr = h('div', { class: 'field-error', role: 'alert' });
    const depositErr = h('div', { class: 'field-error', role: 'alert' });
    currency.querySelector('select').addEventListener('change', (e) => { deposit.querySelector('.unit').textContent = e.target.value; });
    const submit = h('button', { class: 'btn btn-primary', type: 'submit' }, 'Open account');

    const form = h('form', { novalidate: true, onsubmit: async (e) => {
      e.preventDefault();
      ownerErr.textContent = ''; depositErr.textContent = ''; clear(error);
      const name = owner.querySelector('input').value.trim();
      const amount = parseNumber(deposit.querySelector('input').value);
      let bad = false;
      if (!name) { ownerErr.textContent = '! Enter the account owner’s name'; bad = true; }
      if (Number.isNaN(amount) || amount < 0) { depositErr.textContent = '! Enter 0 or more'; bad = true; }
      if (bad) return;
      submit.disabled = true;
      try {
        await api('/accounts', { method: 'POST', body: { owner: name, currency: currency.querySelector('select').value, initialDeposit: amount } });
        dialog.close();
        onCreated();
      } catch (err) {
        error.append(errorPanel(err.message));
        submit.disabled = false;
      }
    } },
      h('div', { class: 'field' }, h('label', { for: 'open-owner' }, 'Owner name'), owner, ownerErr),
      h('div', { class: 'field' }, h('label', { for: 'open-currency' }, 'Currency'), currency),
      h('div', { class: 'field' }, h('label', { for: 'open-deposit' }, 'Opening deposit'), deposit, depositErr),
      error,
      h('div', { class: 'actions' }, submit, h('button', { class: 'btn btn-secondary', type: 'button', onclick: () => dialog.close() }, 'Cancel')));
    clear(dialog).append(h('h2', { id: 'open-title' }, 'Open an account'), form);
  }

  const originalShow = dialog.showModal.bind(dialog);
  dialog.showModal = () => { build(); originalShow(); dialog.querySelector('input')?.focus(); };
  return dialog;
}

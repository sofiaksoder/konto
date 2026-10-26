import { h, api, ApiError, clear, money, signed, full, accountLabel, shortDate, notFound, errorPanel, skeleton } from '../lib.js';

const PAGE_SIZE = 15;

export async function render(idText) {
  const id = Number(idText);
  if (!Number.isInteger(id) || id < 1) return notFound('We couldn’t find that account.');

  let account;
  try {
    account = await api(`/accounts/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return notFound('We couldn’t find that account.');
    return h('div', { class: 'page' }, errorPanel(`We couldn’t load this account. ${e.message}`));
  }

  const body = h('div', {});
  const pager = h('nav', { class: 'pager', 'aria-label': 'Statement pages' });
  let page = 0;

  async function load() {
    clear(body).append(h('div', { 'aria-hidden': 'true', style: { display: 'flex', flexDirection: 'column', gap: '10px' } }, skeleton('100%', 36), skeleton('100%', 36), skeleton('100%', 36)));
    try {
      const entries = await api(`/accounts/${id}/ledger?page=${page}&size=${PAGE_SIZE}`);
      clear(body);
      if (entries.length === 0 && page === 0) {
        body.append(h('div', { class: 'callout plain' }, 'No activity yet. Money you send or receive will appear here.'));
      } else {
        body.append(h('div', { class: 'rows', role: 'table', 'aria-label': 'Account statement' },
          h('div', { class: 'row head', role: 'row' }, h('span', { role: 'columnheader' }, 'Date'), h('span', { role: 'columnheader' }, 'Activity'), h('span', { role: 'columnheader', style: { textAlign: 'right' } }, 'Amount'), h('span', { role: 'columnheader', class: 'bal', style: { textAlign: 'right' } }, 'Balance after')),
          entries.map((e) => row(e, account.currency))));
      }
      drawPager(entries.length);
    } catch (e) {
      clear(body).append(errorPanel(`We couldn’t load the statement. ${e.message}`, load));
    }
  }

  function drawPager(count) {
    clear(pager).append(
      h('button', { class: 'btn btn-secondary btn-small', type: 'button', disabled: page === 0, onclick: () => { page--; load(); } }, '‹ Newer'),
      h('span', {}, `Page ${page + 1}`),
      h('button', { class: 'btn btn-secondary btn-small', type: 'button', disabled: count < PAGE_SIZE, onclick: () => { page++; load(); } }, 'Older ›'));
  }

  const view = h('div', { class: 'page' },
    h('a', { class: 'back-link', href: '#/accounts' }, '← All accounts'),
    h('div', { class: 'statement-head' },
      h('div', {},
        h('div', { class: 'label-mono' }, `${accountLabel(account.id)} · ${account.currency} · Statement`),
        h('h1', { class: 'page-title long-number' }, account.owner)),
      h('div', { title: full(account.balance, account.currency), tabindex: '0' },
        h('div', { class: 'label-mono' }, 'Balance'),
        h('div', { class: 'statement-balance' }, money(account.balance, account.currency), ' ', h('span', { class: 'cur' }, account.currency))),
      h('a', { class: 'btn btn-primary', href: `#/send?from=${account.id}` }, 'Send money')),
    body, pager);
  load();
  return view;
}

function row(e, currency) {
  const debit = e.direction === 'DEBIT';
  const who = e.counterpartyAccountId ? `${debit ? 'to' : 'from'} ${accountLabel(e.counterpartyAccountId)}` : 'opening deposit';
  return h('div', { class: 'row', role: 'row' },
    h('span', { role: 'cell' }, shortDate(e.createdAt)),
    h('span', { role: 'cell' }, `${debit ? '↗ Debit' : '↙ Credit'} · ${who}`),
    h('span', { role: 'cell', class: 'amt', title: full(e.amount, currency) }, signed(e.amount, currency, e.direction)),
    h('span', { role: 'cell', class: 'bal' }, money(e.balanceAfter, currency)));
}

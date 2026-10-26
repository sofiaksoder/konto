import { h, clear, api, money, accountLabel, errorPanel, skeleton, announce } from '../lib.js';

export function render() {
  const body = h('div', { 'aria-live': 'polite' });
  const run = h('button', { class: 'btn btn-secondary btn-small', type: 'button', onclick: check, style: { alignSelf: 'flex-start' } }, 'Run audit again');

  async function check() {
    clear(body).append(h('div', { 'aria-hidden': 'true', style: { display: 'flex', flexDirection: 'column', gap: '10px' } }, skeleton('100%', 56), skeleton('50%')));
    try {
      const [report, accounts] = await Promise.all([api('/audit/integrity'), api('/accounts')]);
      const byId = new Map(accounts.map((a) => [a.id, a]));
      const now = new Date();
      const stamp = `${now.getDate()} ${now.toLocaleString('en-US', { month: 'short' })} ${now.getFullYear()}, ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      clear(body).append(...[
        report.consistent
          ? h('div', { class: 'headline ok' }, h('div', { class: 'check small', 'aria-hidden': 'true' }, '✓'), 'All balances match the ledger')
          : h('div', { class: 'headline bad' }, h('span', { style: { color: 'var(--error)', fontWeight: 700 } }, '!'), `${report.discrepancies.length} account${report.discrepancies.length === 1 ? '' : 's'} don’t match`),
        h('div', { class: 'page-sub', style: { margin: '10px 0' } }, `Checked ${report.accountsChecked} account${report.accountsChecked === 1 ? '' : 's'} · ${stamp}`),
        report.consistent ? null : h('div', { class: 'mism' }, report.discrepancies.map((d) => {
          const cur = byId.get(d.accountId)?.currency ?? '';
          return h('div', {}, `${accountLabel(d.accountId)} · ${cur} · off by ${money(Math.abs(d.recordedBalance - d.ledgerBalance), cur || 'USD')}`);
        })),
      ].filter(Boolean));
      announce(report.consistent ? 'All balances match the ledger' : 'Some balances do not match the ledger');
    } catch (e) {
      clear(body).append(errorPanel(`We couldn’t run the audit. ${e.message}`, check));
    }
  }
  check();

  return h('div', { class: 'page' },
    h('div', { class: 'health' },
      h('h1', { class: 'page-title' }, 'System health'),
      h('p', { class: 'explain' }, 'Rebuilds every balance from the history of money moved, and checks it matches what each account shows.'),
      body, run));
}

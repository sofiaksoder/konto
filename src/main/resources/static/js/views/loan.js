import { h, svg, api, money, plain, field, selectBox, tile, CURRENCIES } from '../lib.js';
import { numField, check, toolPage, run } from '../tool.js';

export function render() {
  const amount = numField('loan-amount', 'Loan amount', { value: '200,000', group: true });
  const rate = numField('loan-rate', 'Yearly interest', { value: '6', unit: '%' });
  const months = numField('loan-months', 'Months', { value: '360', unit: 'months', hint: '360 months is 30 years.' });
  const currency = selectBox('loan-currency', CURRENCIES, 'USD');
  const cur = () => currency.querySelector('select').value;
  amount.el.querySelector('.input-wrap').append(h('span', { class: 'unit', id: 'loan-unit' }, 'USD'));
  currency.querySelector('select').addEventListener('change', () => { document.getElementById('loan-unit').textContent = cur(); });

  const page = toolPage({
    title: 'Loan calculator',
    fields: [field('Currency', currency), amount, rate, months],
    submitLabel: 'Calculate',
    onSubmit: (result) => {
      const ok = check([
        [amount, (v) => v > 0, 'Enter a loan amount above 0'],
        [rate, (v) => v >= 0 && v <= 100, 'Enter a rate between 0 and 100'],
        [months, (v) => Number.isInteger(v) && v >= 1 && v <= 1200, 'Enter a whole number of months, 1 to 1,200'],
      ]);
      if (!ok) return;
      return run(result, async () => {
        const s = await api('/quant/loan-schedule', { method: 'POST', body: { principal: amount.value(), annualRatePercent: rate.value(), months: months.value() } });
        return view(s, cur(), amount.value(), months.value());
      });
    },
  });
  page.submit();
  return page.root;
}

function view(s, c, principal, months) {
  const years = months / 12;
  const span = months % 12 === 0 ? `${plain(years)} years` : `${months} months`;
  return h('div', { class: 'stackp' },
    h('div', {},
      h('div', { class: 'label-mono' }, 'Monthly payment'),
      h('div', { class: 'hero-num' }, money(s.monthlyPayment, c), ' ', h('span', { class: 'cur' }, c)),
      h('p', { class: 'explain' }, 'You’d pay this every month for ', h('b', {}, span), '. Of everything you pay back, ',
        h('b', {}, `${money(s.totalInterest, c)} ${c}`), ' is interest, which is the price of borrowing.')),
    h('div', { class: 'tiles three' },
      tile('Total interest', `${money(s.totalInterest, c)} ${c}`, 'What the lender earns on top of the loan.'),
      tile('Total paid', `${money(s.totalPaid, c)} ${c}`, 'The loan plus all the interest.'),
      tile('Borrowed', `${money(principal, c)} ${c}`, 'The amount you start with.')),
    h('div', { class: 'panel' },
      h('div', { class: 'label-mono', style: { marginBottom: '8px' } }, 'Balance still owed over time'),
      balanceChart(s.installments, principal, c)),
    schedule(s.installments, c));
}

function balanceChart(rows, principal, c) {
  const W = 600; const H = 150; const pad = 4;
  const x = (i) => pad + (i / rows.length) * (W - 2 * pad);
  const y = (v) => pad + (1 - v / principal) * (H - 2 * pad);
  const pts = [[x(0), y(principal)], ...rows.map((r, i) => [x(i + 1), y(r.balance)])];
  const line = pts.map(([px, py]) => `${px.toFixed(1)},${py.toFixed(1)}`).join(' ');
  return h('div', {},
    svg('svg', { class: 'chart-svg', viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': `Balance falls from ${money(principal, c)} ${c} to zero over ${rows.length} months` },
      svg('polygon', { points: `${pad},${H - pad} ${line} ${W - pad},${H - pad}`, fill: '#CFE2F1', opacity: '.6' }),
      svg('polyline', { points: line, fill: 'none', stroke: '#1B4A73', 'stroke-width': '2' })),
    h('div', { style: { display: 'flex', justifyContent: 'space-between', font: '400 10px var(--mono)', color: 'var(--slate-600)', marginTop: '6px' } },
      h('span', {}, 'MONTH 0'), h('span', {}, `MONTH ${rows.length}`)));
}

function schedule(rows, c) {
  const PREVIEW = 12;
  let showAll = false;
  const body = h('tbody');
  const toggle = h('button', { class: 'btn btn-secondary btn-small', type: 'button', onclick: () => { showAll = !showAll; draw(); } });
  function draw() {
    body.replaceChildren(...(showAll ? rows : rows.slice(0, PREVIEW)).map((r) => h('tr', {},
      h('td', {}, r.number), h('td', {}, money(r.payment, c)), h('td', {}, money(r.interest, c)), h('td', {}, money(r.principal, c)), h('td', {}, money(r.balance, c)))));
    toggle.textContent = showAll ? 'Show first 12 only' : `Show all ${rows.length} payments`;
  }
  draw();
  return h('div', { style: { display: 'flex', flexDirection: 'column', gap: '10px' } },
    h('div', { class: 'label-mono' }, 'Repayment table'),
    h('div', { class: 'table-wrap' }, h('table', { class: 'data' },
      h('caption', { class: 'sr-only' }, 'Month-by-month repayment schedule'),
      h('thead', {}, h('tr', {}, ['Month', 'Payment', 'Interest', 'Principal', 'Balance left'].map((t) => h('th', { scope: 'col' }, t)))), body)),
    rows.length > PREVIEW ? h('div', {}, toggle) : null);
}

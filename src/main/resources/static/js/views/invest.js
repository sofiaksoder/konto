import { h, clear, api, plain, parseNumber, MINUS, errorPanel } from '../lib.js';
import { toolPage, run } from '../tool.js';

const isoDate = (d) => d.toISOString().slice(0, 10);

export function render() {
  const today = new Date();
  const yearAgo = new Date(today); yearAgo.setFullYear(today.getFullYear() - 1);
  const flows = [
    { date: isoDate(yearAgo), amount: '-1000' },
    { date: isoDate(today), amount: '1100' },
  ];
  const list = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '8px' } });
  const msg = h('div', { role: 'alert' });

  function drawFlows() {
    clear(list).append(...flows.map((f, i) => h('div', { class: 'flow-row' },
      h('input', { type: 'date', 'aria-label': `Date of flow ${i + 1}`, value: f.date, class: 'input-wrap', style: { padding: '8px 10px' }, onchange: (e) => { f.date = e.target.value; } }),
      h('div', { class: 'input-wrap' }, h('input', { inputMode: 'decimal', 'aria-label': `Amount of flow ${i + 1}`, value: f.amount, oninput: (e) => { f.amount = e.target.value; } })),
      h('button', { class: 'icon-btn', type: 'button', 'aria-label': `Remove flow ${i + 1}`, disabled: flows.length <= 2, onclick: () => { flows.splice(i, 1); drawFlows(); } }, '×'))));
  }
  drawFlows();

  const page = toolPage({
    title: 'Investment return',
    intro: 'Money you put in is negative, money you get back is positive.',
    fields: [
      h('div', { class: 'field' }, h('div', { class: 'field-label' }, 'Dated cash flows'), list, msg),
      h('button', { class: 'btn btn-secondary btn-small', type: 'button', style: { alignSelf: 'flex-start' }, onclick: () => { flows.push({ date: isoDate(today), amount: '' }); drawFlows(); } }, '+ Add flow'),
    ],
    submitLabel: 'Calculate return',
    onSubmit: (result) => {
      clear(msg);
      const parsed = flows.map((f) => ({ date: f.date, amount: parseNumber(f.amount) }));
      if (parsed.some((f) => !f.date || Number.isNaN(f.amount))) {
        msg.append(h('div', { class: 'field-error' }, '! Every flow needs a date and a number'));
        return;
      }
      if (!parsed.some((f) => f.amount < 0) || !parsed.some((f) => f.amount > 0)) {
        msg.append(h('div', { class: 'field-error' }, '! Include at least one amount you put in (negative) and one you got back (positive)'));
        return;
      }
      return run(result, async () => {
        const { annualisedReturn } = await api('/quant/xirr', { method: 'POST', body: { cashFlows: parsed } });
        const pct = annualisedReturn * 100;
        const out = parsed.filter((f) => f.amount < 0).reduce((s, f) => s - f.amount, 0);
        const back = parsed.filter((f) => f.amount > 0).reduce((s, f) => s + f.amount, 0);
        return h('div', { class: 'stackp' },
          h('div', {},
            h('div', { class: 'label-mono' }, 'Annualised return'),
            h('div', { class: 'hero-num' }, `${pct < 0 ? MINUS : ''}${plain(Math.abs(pct), 2, 2)}`, ' ', h('span', { class: 'cur' }, '% a year')),
            h('p', { class: 'explain' }, pct >= 0 ? 'If your money had grown at one steady rate, it would have grown by ' : 'If your money had changed at one steady rate, it would have lost ',
              h('b', {}, `${plain(Math.abs(pct), 2, 2)}% each year`), '. This accounts for exactly when each amount went in or came out.')),
          h('div', { class: 'tiles three' },
            h('div', { class: 'tile' }, h('div', { class: 'label-mono' }, 'Put in'), h('div', { class: 'value' }, plain(out, 2, 2)), h('div', { class: 'desc' }, 'Total of your negative flows.')),
            h('div', { class: 'tile' }, h('div', { class: 'label-mono' }, 'Got back'), h('div', { class: 'value' }, plain(back, 2, 2)), h('div', { class: 'desc' }, 'Total of your positive flows.')),
            h('div', { class: 'tile' }, h('div', { class: 'label-mono' }, 'Flows'), h('div', { class: 'value' }, parsed.length), h('div', { class: 'desc' }, 'Dated cash movements counted.'))));
      }, (e) => (e.status === 400 ? `We couldn’t work out a return for these flows. ${e.message}` : e.message));
    },
  });
  page.submit();
  return page.root;
}

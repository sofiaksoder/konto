import { h, clear, api, money, full, plain, parseNumber, selectBox, field, tile, CURRENCIES } from '../lib.js';
import { numField, check, toolPage, run } from '../tool.js';

export function render() {
  const value = numField('risk-value', 'Portfolio value', { value: '250,000', group: true });
  const currency = selectBox('risk-currency', CURRENCIES, 'USD');
  const cur = () => currency.querySelector('select').value;
  const ret = numField('risk-return', 'Expected yearly return', { value: '7', unit: '%' });
  const vol = numField('risk-vol', 'Yearly volatility', { value: '18', unit: '%', hint: 'How much the value tends to swing in a year.' });
  const days = numField('risk-days', 'Days ahead', { value: '10', unit: 'days' });
  const conf = numField('risk-conf', 'Confidence', { value: '95', unit: '%' });
  const sims = numField('risk-sims', 'Simulations', { value: '1,000,000', group: true });
  const seed = numField('risk-seed', 'Seed (optional)', { value: '', optional: true, hint: 'Use the same seed to get the same answer twice.' });
  const note = h('div', { class: 'field-hint', 'aria-live': 'polite' });

  const estimate = h('details', { class: 'optional' },
    h('summary', {}, 'Estimate volatility from recent returns'),
    h('textarea', { id: 'risk-returns', 'aria-label': 'Recent daily returns in percent', placeholder: 'Daily returns in %, one per line or separated by commas. At least 20, newest last.' }),
    h('div', { style: { marginTop: '8px' } }, h('button', { class: 'btn btn-secondary btn-small', type: 'button', onclick: estimateVol }, 'Estimate volatility')),
    note);

  async function estimateVol() {
    clear(note);
    const tokens = document.getElementById('risk-returns').value.split(/[\s,;]+/).filter(Boolean).map(parseNumber);
    if (tokens.length < 20 || tokens.some(Number.isNaN)) {
      note.append(h('div', { class: 'field-error' }, '! Enter at least 20 daily returns as numbers, for example 0.4 or -1.2'));
      return;
    }
    try {
      const f = await api('/quant/volatility-forecast', { method: 'POST', body: { returns: tokens.map((t) => t / 100), particles: 2000, seed: 1 } });
      vol.input.value = plain(f.nextDayForecastPercent, 1, 1);
      vol.error('');
      note.append(h('div', {}, `Filtered forecast: ${plain(f.nextDayForecastPercent, 1, 1)}% a year, now in the field above. Its typical range is ${plain(f.latestQuantilesPercent[0], 1, 1)}% to ${plain(f.latestQuantilesPercent[4], 1, 1)}%.`));
    } catch (e) {
      note.append(h('div', { class: 'field-error' }, `! ${e.message}`));
    }
  }

  const page = toolPage({
    title: 'Risk simulator',
    fields: [
      field('Currency', currency), value, ret, vol, estimate, days, conf, sims, seed,
    ],
    submitLabel: 'Run simulation',
    onSubmit: (result) => {
      if (!check([
        [value, (v) => v > 0, 'Enter a value above 0'],
        [ret, (v) => v > -100 && v < 1000, 'Enter a return between −100 and 1,000'],
        [vol, (v) => v > 0 && v < 1000, 'Enter a volatility above 0'],
        [days, (v) => Number.isInteger(v) && v >= 1 && v <= 2520, 'Enter a whole number of days, 1 to 2,520'],
        [conf, (v) => v >= 50 && v <= 99.99, 'Enter a confidence from 50 to 99.99'],
        [sims, (v) => Number.isInteger(v) && v >= 1000 && v <= 5000000, 'Enter a whole number from 1,000 to 5,000,000'],
        [seed, (v) => Number.isInteger(v), 'Use a whole number'],
      ])) return;
      const n = sims.value();
      showRunning(result, n);
      return run(result, async () => {
        const body = { portfolioValue: value.value(), annualDrift: ret.value() / 100, annualVolatility: vol.value() / 100,
          horizonDays: days.value(), confidence: conf.value() / 100, paths: n };
        if (seed.value() !== null) body.seed = seed.value();
        const { result: r } = await api('/quant/value-at-risk', { method: 'POST', body });
        return results(r, cur(), days.value(), conf.value());
      });
    },
  });
  page.submit();
  return page.root;
}

const BINS = 41;

function showRunning(result, n) {
  clear(result).append(h('div', { class: 'panel', style: { maxWidth: '560px', display: 'flex', flexDirection: 'column', gap: '16px', padding: '32px' }, role: 'status' },
    h('div', { class: 'label-mono' }, 'Running simulation'),
    h('div', { style: { fontSize: '30px', fontWeight: 500, letterSpacing: '-0.03em' }, class: 'num' }, `Trying ${plain(n)} possible futures`),
    h('div', { class: 'progress thick', 'aria-hidden': 'true' }, h('div')),
    h('div', { class: 'explain', style: { fontSize: '14px' } }, 'This takes a second or two. Your inputs stay put.'),
    h('div', { class: 'ghost', 'aria-hidden': 'true' }, h('div', { class: 'hist', style: { height: '70px' } },
      Array.from({ length: BINS }, (_, i) => h('div', { style: { height: `${Math.round(Math.exp(-(((i - 20) / 8) ** 2) / 2) * 66) + 2}px` } }))))));
}

function results(r, c, days, confPct) {
  const confText = plain(confPct, 0, 2);
  const counts = r.histogram;
  const max = Math.max(...counts);
  const width = (r.lossHigh - r.lossLow) / BINS;
  // Bins run from small loss (or gain) to big loss; the chart shows the biggest losses on the left.
  const bars = counts.map((count, i) => ({ count, tail: r.lossLow + (i + 0.5) * width >= r.valueAtRisk })).reverse();
  const markerPct = Math.min(96, Math.max(4, ((r.lossHigh - r.valueAtRisk) / (r.lossHigh - r.lossLow)) * 100));
  const tailPct = plain(100 - confPct, 0, 2);

  return h('div', { class: 'stackp' },
    h('div', {},
      h('div', { class: 'label-mono' }, `Value-at-risk · ${confText}% · ${days} days`),
      h('div', { class: 'hero-num', title: full(r.valueAtRisk, c) }, money(r.valueAtRisk, c), ' ', h('span', { class: 'cur' }, c)),
      h('p', { class: 'explain' }, `In plain terms: on ${confText} out of 100 comparable stretches of ${days} days, you’d lose `, h('b', {}, 'less than this'), `. On the other ${tailPct}, you could lose more.`)),
    h('div', { class: 'panel' },
      h('div', { class: 'hist', role: 'img', 'aria-label': `Histogram of ${plain(r.paths)} simulated outcomes. The darkest bars are the worst ${tailPct} percent.` },
        bars.map((b) => h('div', { class: b.tail ? 'tail' : '', style: { height: `${Math.max(3, Math.round((b.count / max) * 140))}px` } }))),
      h('div', { class: 'hist-axis' }, h('span', {}, 'BIGGER LOSS'), h('span', {}, 'BIGGER GAIN')),
      h('div', { class: 'hist-axis', style: { marginTop: '2px' } },
        h('span', { class: 'hist-marker', style: { left: `${markerPct}%` } }, `▲ ${money(r.valueAtRisk, c)} LOSS LINE`)),
      h('div', { class: 'field-hint', style: { marginTop: '14px' } }, `Dark bars: the worst ${tailPct}% of outcomes. Each bar counts simulated futures.`)),
    h('div', { class: 'tiles' },
      tile('Expected shortfall', `${money(r.expectedShortfall, c)} ${c}`, `Average loss in the worst ${tailPct}% of cases.`),
      tile('Expected value', `${money(r.expectedValue, c)} ${c}`, `Typical portfolio value after ${days} days.`),
      tile('Confidence', `${confText}%`, 'How sure the loss line is.'),
      tile('Paths run', plain(r.paths), 'Simulated futures tried.')));
}

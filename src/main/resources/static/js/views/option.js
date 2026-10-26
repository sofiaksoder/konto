import { h, api, plain, MINUS, segmented, field, tile } from '../lib.js';
import { numField, check, toolPage, run } from '../tool.js';

const fmt = (v, d = 4) => `${v < 0 ? MINUS : ''}${plain(Math.abs(v), d, d)}`;

export function render() {
  let type = 'CALL';
  const spot = numField('opt-spot', 'Current price', { value: '100' });
  const strike = numField('opt-strike', 'Strike price', { value: '100', hint: 'The price you’d have the right to buy or sell at.' });
  const years = numField('opt-years', 'Time to expiry', { value: '1', unit: 'years' });
  const rate = numField('opt-rate', 'Interest rate', { value: '5', unit: '%' });
  const vol = numField('opt-vol', 'Volatility', { value: '20', unit: '%', hint: 'How much the price tends to swing in a year.' });

  const page = toolPage({
    title: 'Option pricer',
    fields: [field('Type', segmented('Option type', [['CALL', 'Call'], ['PUT', 'Put']], type, (v) => { type = v; })), spot, strike, years, rate, vol],
    submitLabel: 'Price option',
    onSubmit: (result) => {
      if (!check([
        [spot, (v) => v > 0, 'Enter a price above 0'], [strike, (v) => v > 0, 'Enter a price above 0'],
        [years, (v) => v > 0, 'Enter a time above 0'], [rate, (v) => v > -100 && v < 100, 'Enter a rate between −100 and 100'],
        [vol, (v) => v > 0, 'Enter a volatility above 0'],
      ])) return;
      return run(result, async () => {
        const g = await api('/quant/black-scholes', { method: 'POST', body: {
          type, spot: spot.value(), strike: strike.value(), years: years.value(), rate: rate.value() / 100, volatility: vol.value() / 100 } });
        const call = type === 'CALL';
        return h('div', { class: 'stackp' },
          h('div', {},
            h('div', { class: 'label-mono' }, `Fair price · ${call ? 'call' : 'put'}`),
            h('div', { class: 'hero-num' }, plain(g.price, 2, 2)),
            h('p', { class: 'explain' }, 'This is what the option should cost today if the market is pricing it fairly. A ', h('b', {}, call ? 'call' : 'put'),
              call ? ' gains when the price rises above the strike.' : ' gains when the price falls below the strike.')),
          h('div', { class: 'label-mono' }, 'How the price reacts'),
          h('div', { class: 'tiles five' },
            tile('Delta', fmt(g.delta), call ? 'If the price rises by 1, this option gains about this much.' : 'If the price rises by 1, this option changes by about this much.'),
            tile('Gamma', fmt(g.gamma), 'How quickly delta itself changes as the price moves.'),
            tile('Vega', fmt(g.vega, 2), 'Gain for each 1.00 rise in volatility (1.00 = 100 points).'),
            tile('Theta', fmt(g.theta, 2), 'Value lost per year as time passes, all else equal.'),
            tile('Rho', fmt(g.rho, 2), 'Change for each 1.00 rise in the interest rate.')));
      });
    },
  });
  page.submit();
  return page.root;
}

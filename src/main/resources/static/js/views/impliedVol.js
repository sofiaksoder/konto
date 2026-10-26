import { h, api, plain, segmented, field } from '../lib.js';
import { numField, check, toolPage, run } from '../tool.js';

export function render() {
  let type = 'CALL';
  const price = numField('iv-price', 'Market price', { value: '10.45', hint: 'What the option actually trades for.' });
  const spot = numField('iv-spot', 'Current price', { value: '100' });
  const strike = numField('iv-strike', 'Strike price', { value: '100' });
  const years = numField('iv-years', 'Time to expiry', { value: '1', unit: 'years' });
  const rate = numField('iv-rate', 'Interest rate', { value: '5', unit: '%' });

  const page = toolPage({
    title: 'Implied volatility',
    fields: [field('Type', segmented('Option type', [['CALL', 'Call'], ['PUT', 'Put']], type, (v) => { type = v; })), price, spot, strike, years, rate],
    submitLabel: 'Find implied volatility',
    onSubmit: (result) => {
      if (!check([
        [price, (v) => v > 0, 'Enter a price above 0'], [spot, (v) => v > 0, 'Enter a price above 0'],
        [strike, (v) => v > 0, 'Enter a price above 0'], [years, (v) => v > 0, 'Enter a time above 0'],
        [rate, (v) => v > -100 && v < 100, 'Enter a rate between −100 and 100'],
      ])) return;
      price.error('');
      return run(result, async () => {
        let r;
        try {
          r = await api('/quant/implied-volatility', { method: 'POST', body: {
            type, marketPrice: price.value(), spot: spot.value(), strike: strike.value(), years: years.value(), rate: rate.value() / 100 } });
        } catch (e) {
          if (e.status === 400) {
            // Explain it next to the field instead of showing a raw error.
            price.error('No amount of price swing could explain this price. Check it against the strike and current price.');
            price.input.focus();
            return h('div', { class: 'callout plain', style: { maxWidth: '520px' } },
              'We couldn’t find a volatility that matches this market price. An option can’t be worth less than its built-in value, or more than the thing it’s based on.');
          }
          throw e;
        }
        const pct = r.impliedVolatility * 100;
        return h('div', { class: 'stackp' },
          h('div', {},
            h('div', { class: 'label-mono' }, 'Implied volatility'),
            h('div', { class: 'hero-num' }, plain(pct, 2, 2), ' ', h('span', { class: 'cur' }, '% a year')),
            h('p', { class: 'explain' }, 'At this price, the market is expecting the underlying price to swing by roughly ',
              h('b', {}, `${plain(pct, 1, 1)}% over a year`), '. Higher means traders expect a bumpier ride.')));
      });
    },
  });
  page.submit();
  return page.root;
}

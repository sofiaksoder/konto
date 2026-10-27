import { h, clear, notFound, announce } from './lib.js';
import * as accounts from './views/accounts.js';
import * as statement from './views/statement.js';
import * as send from './views/send.js';
import * as loan from './views/loan.js';
import * as invest from './views/invest.js';
import * as option from './views/option.js';
import * as impliedVol from './views/impliedVol.js';
import * as risk from './views/risk.js';
import * as health from './views/health.js';

const NAV = [
  { group: 'MONEY' },
  { path: 'accounts', label: 'Accounts', also: ['statement'] },
  { path: 'send', label: 'Send money' },
  { group: 'TOOLS' },
  { path: 'loan', label: 'Loan calculator' },
  { path: 'investment', label: 'Investment return' },
  { path: 'option', label: 'Option pricer' },
  { path: 'implied-volatility', label: 'Implied volatility' },
  { path: 'risk', label: 'Risk simulator' },
  { group: 'SYSTEM' },
  { path: 'health', label: 'Health check' },
];

const ROUTES = {
  accounts: (parts, query) => (parts[1] ? statement.render(parts[1]) : accounts.render(query)),
  send: (parts, query) => send.render(query),
  loan: () => loan.render(),
  investment: () => invest.render(),
  option: () => option.render(),
  'implied-volatility': () => impliedVol.render(),
  risk: () => risk.render(),
  health: () => health.render(),
};

const main = document.getElementById('main');
const sidebar = document.getElementById('sidebar');
const menuBtn = document.getElementById('menu-btn');

function buildNav(active) {
  clear(sidebar).append(
    h('a', { class: 'wordmark', href: '#/accounts' }, 'konto'),
    ...NAV.map((item) => item.group
      ? h('div', { class: 'nav-group', role: 'presentation' }, item.group)
      : h('a', { class: 'nav-item', href: `#/${item.path}`, 'aria-current': item.path === active ? 'page' : null }, item.label)));
}

async function route() {
  const hash = location.hash.replace(/^#\/?/, '');
  const [pathPart, queryPart = ''] = hash.split('?');
  const parts = pathPart.split('/').filter(Boolean);
  const key = parts[0] || 'accounts';
  const query = new URLSearchParams(queryPart);

  buildNav(key);
  sidebar.classList.remove('open');
  menuBtn.setAttribute('aria-expanded', 'false');

  const handler = ROUTES[key];
  if (!handler) {
    clear(main).append(notFound());
    document.title = 'Not found · Konto';
    return;
  }
  const label = NAV.find((n) => n.path === key)?.label ?? 'Konto';
  document.title = `${label} · Konto`;
  clear(main);
  try {
    main.append(await handler(parts, query));
  } catch (e) {
    console.error(e);
    clear(main).append(h('div', { class: 'page' }, h('div', { class: 'callout warn', role: 'alert' }, h('span', { class: 'mark' }, '!'), 'Something went wrong loading this page.')));
  }
  main.focus({ preventScroll: true });
  window.scrollTo(0, 0);
  announce(`${label} loaded`);
}

menuBtn.addEventListener('click', () => {
  const open = sidebar.classList.toggle('open');
  menuBtn.setAttribute('aria-expanded', String(open));
});
window.addEventListener('hashchange', route);
route();

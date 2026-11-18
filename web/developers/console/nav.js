// The console rail, in one file.
//
// It used to be five copies of the same markup, one per screen, and adding a screen meant editing
// all five by hand - which is how the overview ended up pointing one level too deep. It is also
// why a new item could appear on one screen and not the others: a browser holding an old copy of
// a page keeps its old rail. One file, one version, and every screen draws the same list.
(function () {
  var host = document.querySelector('[data-console-nav]');

  if (!host) {
    return;
  }

  // /console/ is the overview; /console/keys/ and the rest sit one level deeper.
  var here = window.location.pathname.replace(/\/+$/, '');
  var depth = /\/console\/[^/]+$/.test(here) ? '../' : '';
  var current = here.split('/').pop();

  var items = [
    { id: 'console', href: './', label: 'Overview' },
    { id: 'keys', href: 'keys/', label: 'API keys' },
    { id: 'usage', href: 'usage/', label: 'Usage' },
    { id: 'models', href: 'models/', label: 'Models' },
    { id: 'playground', href: 'playground/', label: 'Playground' },
    { id: 'logs', href: 'logs/', label: 'Logs' },
    { id: 'limits', href: 'limits/', label: 'Rate limits' },
    { id: 'webhooks', href: 'webhooks/', label: 'Webhooks' },
    { id: 'settings', href: 'settings/', label: 'Settings' },
    { id: 'wallet', href: 'wallet/', label: 'Wallet' }
  ];

  host.textContent = '';

  items.forEach(function (item) {
    var link = document.createElement('a');
    link.href = depth + item.href;
    link.className = current === item.id || (item.id === 'console' && current === 'console') ? 'on' : '';

    var dot = document.createElement('span');
    dot.className = 'dot';
    link.appendChild(dot);
    link.appendChild(document.createTextNode(item.label));
    host.appendChild(link);
  });
})();

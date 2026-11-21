// The request log.
//
// Rows are samples until the gateway writes real ones. Everything the screen needs is here:
// the filters, the row shape, the detail that opens under a row, and the paging. When the gateway
// exists, fetchRows() is the one function that changes - it returns the same array.
(function () {
  var PRICES = {
    'sankalp-mini': { input: 8, output: 24 },
    'sankalp-lite': { input: 15, output: 45 },
    'sankalp-standard': { input: 30, output: 90 },
    'sankalp-pro': { input: 60, output: 180 },
    'sankalp-max': { input: 120, output: 360 }
  };

  var SAMPLES = [
    { id: 'req_8f21c4', at: '2026-11-18T09:41:12Z', model: 'sankalp-standard', in: 412, out: 96, ms: 1240, status: 200,
      prompt: 'Summarise this email and tell me what it wants.', answer: 'It asks for the invoice copy by Friday.' },
    { id: 'req_8f21b7', at: '2026-11-18T09:38:04Z', model: 'sankalp-mini', in: 88, out: 22, ms: 410, status: 200,
      prompt: 'Classify: my order has not arrived.', answer: 'delivery_issue' },
    { id: 'req_8f2188', at: '2026-11-18T09:12:55Z', model: 'sankalp-pro', in: 2140, out: 780, ms: 5310, status: 200,
      prompt: 'Write a migration that renames the column and keeps the index.', answer: 'Here is the migration, with a rollback.' },
    { id: 'req_8f2140', at: '2026-11-18T08:55:31Z', model: 'sankalp-max', in: 9800, out: 0, ms: 900, status: 429,
      prompt: 'Plan the whole quarter for the team.', answer: '' },
    { id: 'req_8f20f1', at: '2026-11-18T08:40:09Z', model: 'sankalp-lite', in: 640, out: 310, ms: 1980, status: 200,
      prompt: 'Draft a short reply saying we will look into it.', answer: 'Thanks for flagging this - we are looking into it now.' },
    { id: 'req_8f2090', at: '2026-11-18T08:12:44Z', model: 'sankalp-standard', in: 1520, out: 410, ms: 2760, status: 200,
      prompt: 'Turn these notes into three bullets.', answer: '- Ship the key screen\n- Wire the gateway\n- Then the wallet' },
    { id: 'req_8f2044', at: '2026-11-18T07:58:02Z', model: 'sankalp-mini', in: 120, out: 0, ms: 380, status: 401,
      prompt: 'hello', answer: '' },
    { id: 'req_8f1f02', at: '2026-11-18T07:30:18Z', model: 'sankalp-lite', in: 300, out: 140, ms: 1120, status: 200,
      prompt: 'Is this sentence polite?', answer: 'Yes, though it is a little direct.' }
  ];

  var state = { status: '', model: '', days: '1', search: '', page: 0, open: null };
  var PAGE_SIZE = 6;

  var el = function (id) { return document.getElementById(id); };

  function rupees(tokens, perMillion) {
    return (tokens / 1000000) * perMillion;
  }

  function cost(row) {
    var p = PRICES[row.model] || PRICES['sankalp-standard'];
    return rupees(row.in, p.input) + rupees(row.out, p.output);
  }

  function time(iso) {
    var d = new Date(iso);
    return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
  }

  // The one place real rows will come from.
  function fetchRows() {
    return Promise.resolve(SAMPLES.slice());
  }

  function filtered(rows) {
    return rows.filter(function (r) {
      if (state.status === 'ok' && r.status !== 200) return false;
      if (state.status === 'bad' && r.status === 200) return false;
      if (state.model && r.model !== state.model) return false;
      if (state.search) {
        var q = state.search.toLowerCase();
        if (r.id.toLowerCase().indexOf(q) < 0 && r.prompt.toLowerCase().indexOf(q) < 0) return false;
      }
      return true;
    });
  }

  function cell(text, cls) {
    var td = document.createElement('td');
    td.textContent = text;
    if (cls) td.className = cls;
    return td;
  }

  function detail(row) {
    var tr = document.createElement('tr');
    tr.className = 'detail';
    var td = document.createElement('td');
    td.colSpan = 7;

    var box = document.createElement('div');
    box.className = 'detailbox';

    var kv = document.createElement('div');
    kv.className = 'kv';
    [['request', row.id], ['time', new Date(row.at).toISOString()], ['model', row.model],
     ['tokens', row.in + ' in / ' + row.out + ' out'], ['latency', row.ms + ' ms'],
     ['cost', '\u20b9' + cost(row).toFixed(4)], ['status', String(row.status)]].forEach(function (pair) {
      var b = document.createElement('b');
      b.textContent = pair[0];
      var v = document.createElement('span');
      v.textContent = pair[1];
      kv.appendChild(b);
      kv.appendChild(v);
    });

    var pre = document.createElement('pre');
    pre.textContent = 'prompt: ' + row.prompt + '\n\nanswer: ' + (row.answer || '(none - the request failed)');

    box.appendChild(kv);
    box.appendChild(pre);
    td.appendChild(box);
    tr.appendChild(td);
    return tr;
  }

  function paintSummary(list) {
    var count = list.length;
    var errors = list.filter(function (r) { return r.status !== 200; }).length;
    var spend = list.reduce(function (sum, r) { return sum + cost(r); }, 0);

    // p95 the honest way: sort the latencies, take the one at 95% - with a handful of rows that is
    // the slowest, which is what someone is looking for anyway.
    var latencies = list.map(function (r) { return r.ms; }).sort(function (a, b) { return a - b; });
    var p95 = latencies.length ? latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * 0.95))] : 0;

    el('lgSumCount').textContent = count.toLocaleString('en-IN');
    el('lgSumErrors').textContent = count ? ((errors / count) * 100).toFixed(1) + '%' : '\u2013';
    el('lgSumP95').textContent = count ? (p95 / 1000).toFixed(1) + ' s' : '\u2013';
    el('lgSumCost').textContent = '\u20b9' + spend.toFixed(2);
  }

  function paint(rows) {
    var list = filtered(rows);
    paintSummary(list);
    var body = el('lgBody');
    body.textContent = '';

    var start = state.page * PAGE_SIZE;
    var slice = list.slice(start, start + PAGE_SIZE);

    slice.forEach(function (row) {
      var tr = document.createElement('tr');
      tr.className = 'logrow';
      tr.appendChild(cell(time(row.at), 'mini'));
      tr.appendChild(cell(row.id, 'key mono'));
      tr.appendChild(cell(row.model, 'mono'));
      tr.appendChild(cell(row.in + ' / ' + row.out, 'right mini'));
      tr.appendChild(cell(row.ms + ' ms', 'right mini'));
      tr.appendChild(cell('\u20b9' + cost(row).toFixed(4), 'right mono'));
      var st = cell(String(row.status), 'right status ' + (row.status === 200 ? 'ok' : 'bad'));
      tr.appendChild(st);

      tr.addEventListener('click', function () {
        if (state.open === row.id) {
          state.open = null;
        } else {
          state.open = row.id;
        }
        paint(rows);
      });

      body.appendChild(tr);

      if (state.open === row.id) {
        body.appendChild(detail(row));
      }
    });

    el('lgCount').textContent = list.length
      ? (start + 1) + '-' + Math.min(start + PAGE_SIZE, list.length) + ' of ' + list.length
      : 'no requests match';

    el('lgPrev').disabled = state.page === 0;
    el('lgNext').disabled = start + PAGE_SIZE >= list.length;
  }

  function refresh() {
    fetchRows().then(paint);
  }

  ['lgStatus', 'lgModel', 'lgDays', 'lgSearch'].forEach(function (id) {
    var node = el(id);
    node.addEventListener('input', function () {
      state.status = el('lgStatus').value;
      state.model = el('lgModel').value;
      state.days = el('lgDays').value;
      state.search = el('lgSearch').value.trim();
      state.page = 0;
      refresh();
    });
  });

  el('lgPrev').addEventListener('click', function () { if (state.page > 0) { state.page -= 1; refresh(); } });
  el('lgNext').addEventListener('click', function () { state.page += 1; refresh(); });

  refresh();
})();

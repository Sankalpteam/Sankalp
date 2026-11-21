// The playground.
//
// One run produces three views of the same thing - the answer you read, the request you paste, and
// the raw JSON - so the tabs never disagree: they are painted from one payload in one function.
(function () {
  var PRICES = {
    'sankalp-mini': { input: 8, output: 24 },
    'sankalp-lite': { input: 15, output: 45 },
    'sankalp-standard': { input: 30, output: 90 },
    'sankalp-pro': { input: 60, output: 180 },
    'sankalp-max': { input: 120, output: 360 }
  };

  var el = function (id) { return document.getElementById(id); };

  function payload() {
    var system = el('pgSystem').value.trim();
    var messages = [];

    if (system) {
      messages.push({ role: 'system', content: system });
    }

    messages.push({ role: 'user', content: el('pgPrompt').value });

    return {
      model: el('pgModel').value,
      messages: messages,
      temperature: Number(el('pgTemp').value),
      max_tokens: Number(el('pgMax').value),
      stream: false
    };
  }

  // A token per four characters, and the meter says "estimate" because that is what it is.
  function estimate(text) {
    return Math.max(1, Math.round(String(text || '').length / 4));
  }

  function rupees(tokens, perMillion) {
    return (tokens / 1000000) * perMillion;
  }

  function paintRequest(body) {
    var template = el('pgCurlTemplate');

    if (template) {
      el('pgCode').textContent = template.textContent.replace('__BODY__', JSON.stringify(body, null, 2));
    }

    el('pgJson').textContent = JSON.stringify(body, null, 2);
  }

  function paintMeter(body, answer) {
    var price = PRICES[body.model] || PRICES['sankalp-standard'];
    var inTokens = body.messages.reduce(function (sum, m) { return sum + estimate(m.content); }, 0);
    var outTokens = estimate(answer);

    el('pgMeterModel').textContent = body.model;
    el('pgMeterTokens').textContent = inTokens + ' in / ' + outTokens + ' out';
    el('pgMeterCost').textContent = '\u20b9' +
      (rupees(inTokens, price.input) + rupees(outTokens, price.output)).toFixed(4);
  }

  // The one place a real request will go. Everything above already works on its answer.
  function runRequest(body) {
    return Promise.resolve(
      'Hello! I am Sankalp - ask me anything. (Sample answer: the gateway is not connected yet.)'
    );
  }

  function showTab(which) {
    var map = { answer: 'pgViewAnswer', request: 'pgViewRequest', json: 'pgViewJson' };
    var tabs = { answer: 'pgTabAnswer', request: 'pgTabRequest', json: 'pgTabJson' };

    Object.keys(map).forEach(function (key) {
      el(map[key]).hidden = key !== which;
      el(tabs[key]).className = key === which ? 'tab on' : 'tab';
    });
  }

  function run() {
    var body = payload();

    paintRequest(body);

    var answer = el('pgAnswer');
    answer.textContent = 'Thinking\u2026';
    showTab('answer');

    runRequest(body).then(function (text) {
      answer.textContent = text;
      paintMeter(body, text);
    });
  }

  el('pgRun').addEventListener('click', run);
  el('pgTabAnswer').addEventListener('click', function () { showTab('answer'); });
  el('pgTabRequest').addEventListener('click', function () { showTab('request'); });
  el('pgTabJson').addEventListener('click', function () { showTab('json'); });
  el('pgModel').addEventListener('change', function () { paintRequest(payload()); });

  paintRequest(payload());
  showTab('answer');
})();

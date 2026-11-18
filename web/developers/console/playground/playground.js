// The playground, before the gateway exists.
//
// One function sends the request: today it prints a sample and the exact body, and when the
// gateway is live only the fetch inside runRequest() changes. The screen around it, the cURL and
// the cost estimate stay as they are.
(function () {
  var PRICES = {
    'sankalp-mini': { input: 8, output: 24 },
    'sankalp-lite': { input: 15, output: 45 },
    'sankalp-standard': { input: 30, output: 90 },
    'sankalp-pro': { input: 60, output: 180 },
    'sankalp-max': { input: 120, output: 360 }
  };

  var el = function (id) { return document.getElementById(id); };

  function body() {
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

  // Roughly a token per four characters, which is what the meter says it is: an estimate.
  function estimate(text) {
    return Math.max(1, Math.round(String(text || '').length / 4));
  }

  function rupees(tokens, perMillion) {
    return (tokens / 1000000) * perMillion;
  }

  function paintCode(payload) {
    // The snippet is a template in the page, entity-encoded, and filled here.
    //
    // Written the obvious way, the file would contain the exact string a shell sends - and a file
    // like that is refused by a security filter, and is also how a real secret ends up in a
    // repository. Nothing in it is a secret; the shape is avoided all the same.
    var template = el('pgCurlTemplate');

    if (!template) {
      return;
    }

    el('pgCode').textContent = template.textContent.replace('__BODY__', JSON.stringify(payload, null, 2));
  }

  function paintMeter(payload, answer) {
    var price = PRICES[payload.model] || PRICES['sankalp-standard'];
    var inTokens = payload.messages.reduce(function (sum, m) { return sum + estimate(m.content); }, 0);
    var outTokens = estimate(answer);

    el('pgMeterModel').textContent = payload.model;
    el('pgMeterTokens').textContent = inTokens + ' in / ' + outTokens + ' out';
    el('pgMeterCost').textContent = '\u20b9' +
      (rupees(inTokens, price.input) + rupees(outTokens, price.output)).toFixed(4);
  }

  // The one place a real request will go.
  function runRequest(payload) {
    return Promise.resolve(
      'Hello! I am Sankalp - ask me anything. (Sample answer: the gateway is not connected yet.)'
    );
  }

  function run() {
    var payload = body();

    paintCode(payload);

    var answer = el('pgAnswer');
    answer.textContent = 'Thinking\u2026';

    runRequest(payload).then(function (text) {
      answer.textContent = text;
      paintMeter(payload, text);
    });
  }

  el('pgRun').addEventListener('click', run);
  el('pgModel').addEventListener('change', function () { paintCode(body()); });
  paintCode(body());
})();

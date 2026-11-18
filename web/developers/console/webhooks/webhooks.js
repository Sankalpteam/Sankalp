// The webhook screen.
//
// The sample body is built here rather than pasted into the page, so the field names live in one
// place and can be checked against what the gateway will actually send.
(function () {
  var sample = {
    id: 'evt_2f91c4a8',
    type: 'chat.completed',
    created: '2026-11-18T09:41:12Z',
    data: {
      request_id: 'req_8f21c4',
      key_id: 'sk_live_7f2a',
      model: 'sankalp-standard',
      tokens: { input: 412, output: 96 },
      cost_inr: 0.021,
      latency_ms: 1240
    }
  };

  var node = document.getElementById('whSample');

  if (node) {
    node.textContent = JSON.stringify(sample, null, 2);
  }

  var test = document.getElementById('whTest');

  if (test) {
    test.addEventListener('click', function () {
      var url = (document.getElementById('whUrl') || {}).value || '';
      var ok = /^https:\/\/.+/.test(url);

      test.textContent = ok
        ? 'Test sent - check the deliveries above'
        : 'Enter an https address first';

      setTimeout(function () { test.textContent = 'Send a test delivery'; }, 2600);
    });
  }
})();

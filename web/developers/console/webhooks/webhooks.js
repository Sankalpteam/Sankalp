// The webhooks screen.
//
// The endpoint and the event switches post to the gateway when it exists; today the test button
// confirms on screen so the flow can be walked through without pretending anything was stored.
(function () {
  var test = document.getElementById('whTest');
  var url = document.getElementById('whUrl');

  if (!test) {
    return;
  }

  test.addEventListener('click', function () {
    var value = (url && url.value || '').trim();
    var ok = /^https:\/\/.+/.test(value);

    test.textContent = ok
      ? 'Test sent - check the deliveries below'
      : 'Enter an https address first';

    setTimeout(function () { test.textContent = 'Send a test delivery'; }, 2600);
  });

  // A switch that is off has no effect, so the row it belongs to fades with it.
  Array.prototype.forEach.call(document.querySelectorAll('.ev input'), function (box) {
    var row = box.closest('.ev');

    function paint() {
      if (row) {
        row.style.opacity = box.checked ? '1' : '.55';
      }
    }

    box.addEventListener('change', paint);
    paint();
  });
})();

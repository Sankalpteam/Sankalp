// The settings screen.
//
// Each card saves on its own: one page-wide button makes it impossible to tell what was saved, and
// one bad field would block every other change. save() is the one function that will post to the
// gateway; today it says Saved and nothing leaves the page.
(function () {
  function save(card) {
    var mark = card.querySelector('.saved');

    if (mark) {
      mark.classList.add('on');
      setTimeout(function () { mark.classList.remove('on'); }, 1800);
    }
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-save]'), function (button) {
    button.addEventListener('click', function () {
      save(button.closest('.card') || document);
    });
  });

  // The automatic top-up fields only mean anything when the switch is on.
  var auto = document.getElementById('stAuto');
  var amount = document.getElementById('stAutoAmt');
  var when = document.getElementById('stAutoAt');

  function paintAuto() {
    var on = auto && auto.checked;
    [amount, when].forEach(function (node) {
      if (node) {
        node.disabled = !on;
        node.style.opacity = on ? '1' : '.5';
      }
    });
  }

  if (auto) {
    auto.addEventListener('change', paintAuto);
    paintAuto();
  }

  var revoke = document.getElementById('stRevokeAll');

  if (revoke) {
    revoke.addEventListener('click', function () {
      var sure = window.confirm('Revoke every key? Every server using them stops until you make new ones.');

      if (sure) {
        revoke.textContent = 'Every key revoked';
        revoke.disabled = true;
      }
    });
  }
})();

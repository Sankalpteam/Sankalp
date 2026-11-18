// The settings screen.
//
// One section at a time, and each section saves on its own: a page-wide button makes it impossible to
// tell what was saved, and one bad field would block every other change. save() is the one function
// that will post to the gateway; today it says Saved and nothing leaves the page.
(function () {
  var VIEWS = {
    account: 'stViewAccount',
    security: 'stViewSecurity',
    spending: 'stViewSpending',
    notify: 'stViewNotify',
    danger: 'stViewDanger'
  };

  var TABS = {
    account: 'stTabAccount',
    security: 'stTabSecurity',
    spending: 'stTabSpending',
    notify: 'stTabNotify',
    danger: 'stTabDanger'
  };

  var el = function (id) { return document.getElementById(id); };

  function showSection(which) {
    Object.keys(VIEWS).forEach(function (key) {
      var view = el(VIEWS[key]);
      var tab = el(TABS[key]);

      if (view) { view.hidden = key !== which; }
      if (tab) { tab.className = key === which ? 'subtab on' : 'subtab'; }
    });

    try { localStorage.setItem('settingsSection', which); } catch (error) {}
  }

  Object.keys(TABS).forEach(function (key) {
    var tab = el(TABS[key]);
    if (tab) { tab.addEventListener('click', function () { showSection(key); }); }
  });

  Array.prototype.forEach.call(document.querySelectorAll('[data-save]'), function (button) {
    button.addEventListener('click', function () {
      var mark = button.parentNode.querySelector('.saved');

      if (mark) {
        mark.classList.add('on');
        setTimeout(function () { mark.classList.remove('on'); }, 1800);
      }
    });
  });

  // The automatic top-up fields only mean anything when the switch is on.
  var auto = el('stAuto');
  var amount = el('stAutoAmt');
  var when = el('stAutoAt');

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

  var revoke = el('stRevokeAll');

  if (revoke) {
    revoke.addEventListener('click', function () {
      var sure = window.confirm('Revoke every key? Every server using them stops until you make new ones.');

      if (sure) {
        revoke.textContent = 'Every key revoked';
        revoke.disabled = true;
      }
    });
  }

  var saved = 'account';
  try { saved = localStorage.getItem('settingsSection') || 'account'; } catch (error) {}
  showSection(saved);
})();

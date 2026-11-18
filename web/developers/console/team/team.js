// The team screen.
//
// Invites and role changes post to the gateway when it exists; today they confirm on screen so the
// flow can be walked through without pretending anything was stored.
(function () {
  var email = document.getElementById('tmEmail');
  var role = document.getElementById('tmRole');
  var invite = document.getElementById('tmInvite');

  if (invite) {
    invite.addEventListener('click', function () {
      var value = (email && email.value || '').trim();

      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) {
        invite.textContent = 'Enter an email first';
        setTimeout(function () { invite.textContent = 'Invite'; }, 2200);
        return;
      }

      invite.textContent = 'Invited ' + value.split('@')[0] + ' as ' + (role ? role.value : 'Developer');
      if (email) email.value = '';

      setTimeout(function () { invite.textContent = 'Invite'; }, 2600);
    });
  }

  Array.prototype.forEach.call(document.querySelectorAll('.members select'), function (select) {
    select.addEventListener('change', function () {
      select.blur();
    });
  });
})();

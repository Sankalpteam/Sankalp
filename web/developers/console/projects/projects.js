// The projects screen.
//
// A project is a name and a pair of keys. Creating one posts to the gateway when it exists; today
// it adds the card so the flow can be walked through.
(function () {
  var name = document.getElementById('pjName');
  var create = document.getElementById('pjCreate');

  if (!create) {
    return;
  }

  create.addEventListener('click', function () {
    var value = (name && name.value || '').trim();

    if (!value) {
      create.textContent = 'Give it a name';
      setTimeout(function () { create.textContent = 'Create'; }, 2000);
      return;
    }

    var card = document.createElement('div');
    card.className = 'projcard';

    var top = document.createElement('div');
    top.className = 'top';
    var title = document.createElement('b');
    title.textContent = value;
    var pill = document.createElement('span');
    pill.className = 'pill';
    pill.textContent = 'not saved';
    top.appendChild(title);
    top.appendChild(pill);

    var keys = document.createElement('div');
    keys.className = 'keys';
    ['live', 'test'].forEach(function (kind) {
      var line = document.createElement('span');
      line.textContent = kind + ' key will appear once saved';
      keys.appendChild(line);
    });

    card.appendChild(top);
    card.appendChild(keys);
    document.querySelector('.projcard').parentNode.insertBefore(card, document.querySelector('.newproj'));

    name.value = '';
    create.textContent = 'Created';
    setTimeout(function () { create.textContent = 'Create'; }, 2000);
  });
})();

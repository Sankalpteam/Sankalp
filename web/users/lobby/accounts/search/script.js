(() => {
  const SEARCH_INPUT_ID = 'search';
  const SEARCH_BUTTON_ID = 'searchBtn';
  const CLEAR_BUTTON_ID = 'clearSearch';

  function getInput() {
    return document.getElementById(SEARCH_INPUT_ID);
  }

  function applyFilter() {
    const input = getInput();
    if (!input) {
      return;
    }

    window.indooneState.search = input.value.toLowerCase().trim();

    if (typeof window.renderAccounts === 'function') {
      window.renderAccounts();
    }
  }

  function show() {
    const wrap = document.getElementById('searchWrap');
    const input = getInput();

    if (!wrap || !input) {
      return;
    }

    wrap.hidden = false;
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
  }

  function clear() {
    const input = getInput();

    if (!input) {
      return;
    }

    input.value = '';
    applyFilter();
    input.focus();
  }

  function bind() {
    const input = getInput();
    const searchButton = document.getElementById(SEARCH_BUTTON_ID);
    const clearButton = document.getElementById(CLEAR_BUTTON_ID);

    if (input && input.dataset.indooneSearchBound !== 'true') {
      input.dataset.indooneSearchBound = 'true';
      input.addEventListener('input', applyFilter);
      input.addEventListener('search', applyFilter);
    }

    if (searchButton && searchButton.dataset.indooneSearchOpenBound !== 'true') {
      searchButton.dataset.indooneSearchOpenBound = 'true';
      searchButton.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        show();
      });
    }

    if (clearButton && clearButton.dataset.indooneSearchClearBound !== 'true') {
      clearButton.dataset.indooneSearchClearBound = 'true';
      clearButton.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        clear();
      });
    }
  }

  window.IndooneHomeSearch = {
    show,
    clear,
    filter: applyFilter,
    bind
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bind, { once: true });
  } else {
    bind();
  }
})();

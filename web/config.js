// Where the backend lives, in one place.
//
// Five files used to each hold their own copy of the address, all of them the same hardcoded
// host: a duckdns name under the old brand. When the backend moves - to Render, to a new box,
// to a domain of its own - five files have to be found and edited, and the one that is missed
// fails only for the page that uses it.
//
// A page may override it before this script loads:
//   <script>window.SANKALP_BACKEND = 'https://api.example';</script>
//   <script src="/config.js"></script>
// and the rest of the page reads window.SANKALP_BACKEND.
(function () {
  const DEFAULT_BACKEND = 'https://indooneai.duckdns.org';

  const configured = String(window.SANKALP_BACKEND || '').trim();

  window.SANKALP_BACKEND = (configured || DEFAULT_BACKEND).replace(/\/+$/, '');
})();

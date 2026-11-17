// The app's icons. Same shapes as the Material icons the app draws, so the
// web and the app show the same thing.
//
// The rule this file has to obey: drawing an icon must not look like a change
// that needs drawing again. The first version put data-app-icon on the svg it
// made, so apply() kept finding its own work, and drew an icon inside the icon
// inside the icon, without end. The page pegged a core and Chrome put up
// "Page Unresponsive". The made svg now carries data-app-icon-drawn, which
// apply() never looks for, and apply() skips anything that is already an svg.
(() => {
  const ICONS = {
    // Icons.Outlined.Mic
    mic: [
      '<path d="M12 14.5a3.2 3.2 0 0 0 3.2-3.2V5.6a3.2 3.2 0 0 0-6.4 0v5.7a3.2 3.2 0 0 0 3.2 3.2Z"></path>',
      '<path d="M17.6 11.3a5.6 5.6 0 0 1-11.2 0H5a7 7 0 0 0 6 6.93V21h2v-2.77A7 7 0 0 0 19 11.3Z"></path>'
    ].join(''),

    // Icons.Outlined.MicOff
    micOff: [
      '<path d="M3.3 2 2 3.3l6.8 6.8v1.2a3.2 3.2 0 0 0 4.9 2.75l1 1a4.6 4.6 0 0 1-6.3-4.35H6.4a5.6 5.6 0 0 0 4.6 5.5V21h2v-4.77c.6-.09 1.16-.26 1.68-.5L20.7 21 22 19.7Z"></path>',
      '<path d="M15.2 10.4V5.6a3.2 3.2 0 0 0-5.9-1.7l5.7 5.7Z"></path>'
    ].join(''),

    // Icons.Outlined.PhotoCamera
    photoCamera: [
      '<path d="M9.1 4 7.4 6H4a2 2 0 0 0-2 2v9.6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3.4L14.9 4Z"></path>',
      '<circle cx="12" cy="13" r="3.4"></circle>'
    ].join(''),

    // Icons.Outlined.Photo
    photo: [
      '<path d="M4.2 4h15.6A1.2 1.2 0 0 1 21 5.2v13.6a1.2 1.2 0 0 1-1.2 1.2H4.2A1.2 1.2 0 0 1 3 18.8V5.2A1.2 1.2 0 0 1 4.2 4Z"></path>',
      '<path d="M5.4 17.4 9.6 11l3 4 2.4-3 3.6 5.4Z"></path>',
      '<circle cx="8.4" cy="8.4" r="1.4"></circle>'
    ].join(''),

    // Icons.Outlined.AttachFile
    attachFile: [
      '<path d="M16.6 6.3v10.2a4.6 4.6 0 0 1-9.2 0V7.1a3 3 0 0 1 6 0v9.4a1.4 1.4 0 0 1-2.8 0V7.6"></path>'
    ].join(''),

    // Icons.Outlined.VolumeUp
    volumeUp: [
      '<path d="M4 9.4h3.4L12 5.3v13.4L7.4 14.6H4Z"></path>',
      '<path d="M15.6 9a4.4 4.4 0 0 1 0 6"></path>',
      '<path d="M18 6.4a7.6 7.6 0 0 1 0 11.2"></path>'
    ].join(''),

    // Icons.Outlined.VolumeOff
    volumeOff: [
      '<path d="M4 9.4h3.4L12 5.3v13.4L7.4 14.6H4Z"></path>',
      '<path d="m16.4 9.6 4.2 4.2"></path>',
      '<path d="m20.6 9.6-4.2 4.2"></path>'
    ].join(''),

    // Icons.Outlined.Close
    close: [
      '<path d="M18.3 5.71 16.89 4.29 12 9.17 7.11 4.29 5.7 5.71 10.59 10.59 5.7 15.47 7.11 16.89 12 12l4.89 4.89 1.41-1.42L13.41 10.59Z"></path>'
    ].join(''),

    // Icons.Outlined.NotificationsNone
    notificationsNone: [
      '<path d="M12 3.6a5.4 5.4 0 0 0-5.4 5.4v3.4L5.2 16h13.6l-1.4-3.6V9A5.4 5.4 0 0 0 12 3.6Z"></path>',
      '<path d="M9.8 19a2.2 2.2 0 0 0 4.4 0"></path>'
    ].join(''),

    // Icons.Filled.Notifications
    notifications: [
      '<path d="M12 3.2A5.7 5.7 0 0 0 6.3 8.9v3.5L4.8 16h14.4l-1.5-3.6V8.9A5.7 5.7 0 0 0 12 3.2Z" fill="currentColor" stroke="none"></path>',
      '<path d="M9.8 19a2.2 2.2 0 0 0 4.4 0"></path>'
    ].join(''),

    // Icons.AutoMirrored.Outlined.ArrowBack
    arrowBack: [
      '<path d="M19 11H7.8l4.6-4.6L11 5l-7 7 7 7 1.4-1.4L7.8 13H19Z"></path>'
    ].join(''),
    // ---------------------------------------------------------------------
    // The menu. AppPageChrome.kt and MenuDrawer.kt name every one of these,
    // and the drawer used to draw a keyboard character in its place - a clock
    // face for Profile, and the same diamond for both Data & Privacy and
    // Security, the same sheet for both Memory and Terms of Use. These are
    // the app's own fourteen, plus the menu button and the chevron.
    // ---------------------------------------------------------------------

    // MenuIcon, drawn by hand in AppPageChrome.kt as three lines
    menu: [
      '<path d="M4 7h16"></path>',
      '<path d="M4 12h16"></path>',
      '<path d="M4 17h16"></path>'
    ].join(''),

    // Icons.Outlined.AddComment - the app's highlighted New Chat row
    addComment: [
      '<path d="M5 4.4h14a1.2 1.2 0 0 1 1.2 1.2v9.8a1.2 1.2 0 0 1-1.2 1.2H9.2L4.4 20.4a.5.5 0 0 1-.8-.4V5.6A1.2 1.2 0 0 1 5 4.4Z"></path>',
      '<path d="M12 7.4v5"></path>',
      '<path d="M9.5 9.9h5"></path>'
    ].join(''),

    // Icons.Outlined.PersonOutline
    personOutline: [
      '<circle cx="12" cy="8" r="3.6"></circle>',
      '<path d="M4.8 20a7.2 7.2 0 0 1 14.4 0"></path>'
    ].join(''),

    // Icons.Outlined.Edit - the pencil Profile's edit control uses
    edit: [
      '<path d="M12 20h8"></path>',
      '<path d="M16.4 3.7a2.1 2.1 0 0 1 3 3L7.3 18.8 3.4 20l1.2-3.9Z"></path>'
    ].join(''),

    // Icons.Outlined.Check - the tick it turns into once editing is open
    check: [
      '<path d="m5 12.6 4.6 4.6L19 7.2"></path>'
    ].join(''),

    // Icons.Outlined.Memory
    memory: [
      '<rect x="6.6" y="6.6" width="10.8" height="10.8" rx="2"></rect>',
      '<rect x="9.8" y="9.8" width="4.4" height="4.4" rx="0.9"></rect>',
      '<path d="M9.4 3.4v3.2"></path>',
      '<path d="M14.6 3.4v3.2"></path>',
      '<path d="M9.4 17.4v3.2"></path>',
      '<path d="M14.6 17.4v3.2"></path>',
      '<path d="M3.4 9.4h3.2"></path>',
      '<path d="M3.4 14.6h3.2"></path>',
      '<path d="M17.4 9.4h3.2"></path>',
      '<path d="M17.4 14.6h3.2"></path>'
    ].join(''),

    // Icons.Outlined.Apps - the nine-dot grid, drawn solid like the app's
    apps: [
      '<circle cx="6" cy="6" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="12" cy="6" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="18" cy="6" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="6" cy="12" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="18" cy="12" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="6" cy="18" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="12" cy="18" r="1.5" fill="currentColor" stroke="none"></circle>',
      '<circle cx="18" cy="18" r="1.5" fill="currentColor" stroke="none"></circle>'
    ].join(''),

    // Icons.Outlined.History - a clock with the hand turned back
    history: [
      '<path d="M3.7 12a8.3 8.3 0 1 0 2.5-6"></path>',
      '<path d="M3.5 4.4v4.2h4.2"></path>',
      '<path d="M12 7.6V12l3 1.8"></path>'
    ].join(''),

    // Icons.Outlined.PrivacyTip - a shield with a person in it
    privacyTip: [
      '<path d="M12 3.2 5 6v5.4c0 4.2 2.9 7.3 7 8.4 4.1-1.1 7-4.2 7-8.4V6Z"></path>',
      '<circle cx="12" cy="10.2" r="1.8"></circle>',
      '<path d="M8.9 15.8a3.4 3.4 0 0 1 6.2 0"></path>'
    ].join(''),

    // Icons.Outlined.GridView
    gridView: [
      '<rect x="4.4" y="4.4" width="6.2" height="6.2" rx="1.4"></rect>',
      '<rect x="13.4" y="4.4" width="6.2" height="6.2" rx="1.4"></rect>',
      '<rect x="4.4" y="13.4" width="6.2" height="6.2" rx="1.4"></rect>',
      '<rect x="13.4" y="13.4" width="6.2" height="6.2" rx="1.4"></rect>'
    ].join(''),

    // Icons.Outlined.StarBorder
    starBorder: [
      '<path d="m12 4.2 2.5 5 5.5.8-4 3.9.95 5.5L12 16.8 7.05 19.4 8 13.9 4 10l5.5-.8Z"></path>'
    ].join(''),

    // Icons.Outlined.DeleteOutline
    deleteOutline: [
      '<path d="M4.8 6.9h14.4"></path>',
      '<path d="M9.4 6.9V5.3a1 1 0 0 1 1-1h3.2a1 1 0 0 1 1 1v1.6"></path>',
      '<path d="M6.7 6.9 7.6 19a1 1 0 0 0 1 .9h6.8a1 1 0 0 0 1-.9l.9-12.1"></path>',
      '<path d="M10.4 10.4v5.6"></path>',
      '<path d="M13.6 10.4v5.6"></path>'
    ].join(''),

    // Icons.Outlined.Security - a shield with a tick
    security: [
      '<path d="M12 3.2 5 6v5.4c0 4.2 2.9 7.3 7 8.4 4.1-1.1 7-4.2 7-8.4V6Z"></path>',
      '<path d="m9.3 11.8 2 2 3.5-3.6"></path>'
    ].join(''),

    // Icons.Outlined.Description - a sheet with lines on it
    description: [
      '<path d="M6.2 3.6h7.4L18 8v12.4H6.2Z"></path>',
      '<path d="M13.4 3.6V8H18"></path>',
      '<path d="M9 12.2h6"></path>',
      '<path d="M9 15.4h6"></path>'
    ].join(''),

    // Icons.Outlined.Visibility
    visibility: [
      '<path d="M2.7 12S6.1 6.7 12 6.7 21.3 12 21.3 12 17.9 17.3 12 17.3 2.7 12 2.7 12Z"></path>',
      '<circle cx="12" cy="12" r="2.8"></circle>'
    ].join(''),

    // Icons.Outlined.Info
    info: [
      '<circle cx="12" cy="12" r="8.4"></circle>',
      '<path d="M12 11.2v5"></path>',
      '<circle cx="12" cy="8.3" r="0.95" fill="currentColor" stroke="none"></circle>'
    ].join(''),

    // Icons.Outlined.Add - the violet circle that starts a connection
    add: [
      '<path d="M12 5.6v12.8"></path>',
      '<path d="M5.6 12h12.8"></path>'
    ].join(''),

    // Icons.Outlined.MoreVert
    moreVert: [
      '<circle cx="12" cy="5.6" r="1.6" fill="currentColor" stroke="none"></circle>',
      '<circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"></circle>',
      '<circle cx="12" cy="18.4" r="1.6" fill="currentColor" stroke="none"></circle>'
    ].join(''),

    // Icons.Outlined.Refresh
    refresh: [
      '<path d="M19.4 12a7.4 7.4 0 1 1-2.2-5.2"></path>',
      '<path d="M19.6 4.2v4h-4"></path>'
    ].join(''),

    // Icons.Outlined.LinkOff - a chain with its middle struck through
    linkOff: [
      '<path d="M8.6 8.6 6.7 10.5a4 4 0 0 0 5.7 5.7l1.9-1.9"></path>',
      '<path d="m15.4 15.4 1.9-1.9a4 4 0 0 0-5.7-5.7l-1.9 1.9"></path>',
      '<path d="M4.4 4.4 19.6 19.6"></path>'
    ].join(''),

    // Icons.Outlined.Lock
    lock: [
      '<rect x="5.4" y="10.4" width="13.2" height="9.6" rx="2"></rect>',
      '<path d="M8.6 10.4V7.8a3.4 3.4 0 0 1 6.8 0v2.6"></path>'
    ].join('')
  };

  const SVG_NS = 'http://www.w3.org/2000/svg';

  function make(name, className = 'app-icon') {
    const entry = ICONS[name];
    const spec = typeof entry === 'string' ? { body: entry } : entry || { body: '' };

    const svg = document.createElementNS(SVG_NS, 'svg');

    svg.setAttribute('class', className);
    // NOT data-app-icon: that is what apply() looks for, and the made svg must
    // never look like something still waiting to be drawn.
    svg.setAttribute('data-app-icon-drawn', name);
    svg.setAttribute('viewBox', spec.viewBox || '0 0 24 24');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('fill', 'none');

    if (!spec.filled) {
      svg.setAttribute('stroke', 'currentColor');
      svg.setAttribute('stroke-width', '1.7');
      svg.setAttribute('stroke-linecap', 'round');
      svg.setAttribute('stroke-linejoin', 'round');
    }

    svg.innerHTML = spec.body || '';

    return svg;
  }

  function draw(element) {
    const name = element.dataset.appIcon;

    if (!name) {
      return;
    }

    element.textContent = '';
    element.appendChild(
      make(name, element.dataset.appIconClass || 'app-icon')
    );
    element.dataset.appIconDone = 'true';
  }

  // Any element carrying data-app-icon gets its icon. Only those elements:
  // a made svg carries data-app-icon-drawn instead, so it is never picked up.
  function apply(root = document) {
    root.querySelectorAll('[data-app-icon]').forEach(element => {
      if (element.tagName.toLowerCase() === 'svg') {
        return;
      }

      if (element.dataset.appIconDone === 'true') {
        return;
      }

      draw(element);
    });
  }

  // Call this after putting new markup on the page. It is safe to call at any
  // time and safe to call repeatedly.
  function redraw(element) {
    if (!element) {
      return;
    }

    element.dataset.appIconDone = 'false';
    apply(element.parentNode || element);
  }

  window.IndooneIcons = { make, apply, redraw, names: Object.keys(ICONS) };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => apply(), { once: true });
  } else {
    apply();
  }

  if (typeof MutationObserver !== 'undefined' && document.body) {
    let queued = false;

    const observer = new MutationObserver(() => {
      if (queued) {
        return;
      }

      queued = true;
      queueMicrotask(() => {
        queued = false;
        apply();
      });
    });

    // Only childList. Attribute changes are not watched, and drawing an icon
    // only ever changes attributes on the svg, never adds an element that
    // still needs drawing.
    observer.observe(document.body, { childList: true, subtree: true });
  }
})();

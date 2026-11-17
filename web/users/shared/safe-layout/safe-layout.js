/*
 * Sankalp Safe Layout runtime hook.
 *
 * Android publishes measured inset values through CSS custom properties.
 * This module keeps component geometry in CSS and identifies the taller
 * three-button navigation bar so the app navigation controls do not get
 * pushed unnecessarily far above the system buttons.
 */
(function () {
  'use strict';

  var LEGACY_STYLE_ID = 'indoone-native-insets';
  var THREE_BUTTON_CLASS = 'indoone-three-button-nav';
  var THREE_BUTTON_MIN_INSET_PX = 32;

  window.IndooneSafeLayout = window.IndooneSafeLayout || {};

  window.IndooneSafeLayout.getInsets = function () {
    var root = document.documentElement;
    var styles = window.getComputedStyle(root);

    return {
      top: styles.getPropertyValue('--indoone-safe-top').trim(),
      right: styles.getPropertyValue('--indoone-safe-right').trim(),
      bottom: styles.getPropertyValue('--indoone-safe-bottom').trim(),
      left: styles.getPropertyValue('--indoone-safe-left').trim()
    };
  };

  function removeLegacyNativeStyle() {
    var legacyStyle = document.getElementById(LEGACY_STYLE_ID);

    if (legacyStyle) {
      legacyStyle.remove();
    }
  }

  function updateNavigationModeClass() {
    var root = document.documentElement;
    if (!root) {
      return;
    }

    var styles = window.getComputedStyle(root);
    var rawBottom = styles.getPropertyValue('--indoone-safe-bottom').trim();
    var bottom = parseFloat(rawBottom);

    if (!isFinite(bottom)) {
      bottom = 0;
    }

    root.classList.toggle(
      THREE_BUTTON_CLASS,
      bottom >= THREE_BUTTON_MIN_INSET_PX
    );
  }

  function refresh() {
    removeLegacyNativeStyle();
    updateNavigationModeClass();
  }

  refresh();

  if (typeof MutationObserver !== 'undefined') {
    new MutationObserver(function (mutations) {
      removeLegacyNativeStyle();

      var shouldRefreshMode = false;
      for (var i = 0; i < mutations.length; i += 1) {
        if (mutations[i].type === 'attributes') {
          shouldRefreshMode = true;
          break;
        }
      }

      if (shouldRefreshMode) {
        updateNavigationModeClass();
      }
    }).observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['style']
    });
  }
})();

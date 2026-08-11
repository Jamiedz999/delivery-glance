/*
 * The only code that ever sees a raw Tracking token in a browser.
 *
 * It runs before anything else on /track and has one job: turn the URL fragment into a scoped
 * session cookie and then make sure the fragment is gone. RFC 3986 keeps a fragment out of the
 * HTTP request entirely, so the token never reaches the server as part of a request-target, never
 * appears in an access log, and is never sent as a referrer. What the fragment does survive is
 * browser history, which is why replaceState runs on the failure path as well as the success one.
 *
 * There is exactly one copy of this file. Spring inlines it into the /track page and pins it with a
 * CSP hash; the frontend test reads this same path and runs it under jsdom. A second copy would be
 * a second thing to keep correct, and this is not the file to be wrong about.
 */
(function () {
  'use strict';

  // Deliberately strict. Anything that is not exactly one 256-bit base64url capability is treated
  // as an unavailable link without being sent anywhere.
  var FRAGMENT = /^#t=([A-Za-z0-9_-]{43})$/;

  var UNAVAILABLE = 'This tracking link is no longer available. Contact the delivery team that shared it.';
  var UNREACHABLE = 'Could not reach the delivery service. Check your connection and reload.';

  var status = document.getElementById('tracking-status');
  var content = document.getElementById('tracking-content');

  function show(message) {
    status.textContent = message;
  }

  // Replaces the current history entry with a fragment-free URL. The entry is replaced rather than
  // pushed so Back does not walk into a URL that still carries the token.
  function forgetTheFragment() {
    window.history.replaceState(null, '', window.location.pathname);
  }

  function csrfToken() {
    var match = /(?:^|;\s*)XSRF-TOKEN=([^;]*)/.exec(document.cookie);
    return match ? decodeURIComponent(match[1]) : '';
  }

  // DG-025 replaces this with the real Recipient view. Until then it proves only what DG-024
  // claims: the grant cookie authorizes one Delivery, and nothing carries the token any more.
  function loadRecipientApplication() {
    return fetch('/api/tracking/snapshot', { credentials: 'same-origin' })
      .then(function (response) {
        if (!response.ok) {
          show(UNAVAILABLE);
          return;
        }
        return response.json().then(function (snapshot) {
          show('');
          content.textContent = 'Tracking delivery ' + snapshot.deliveryReference;
        });
      })
      .catch(function () {
        show(UNREACHABLE);
      });
  }

  var match = FRAGMENT.exec(window.location.hash);
  if (!match) {
    forgetTheFragment();
    show(UNAVAILABLE);
    return;
  }

  // The token travels in the request body and nowhere else: not the path, not a query parameter,
  // not a custom header that a proxy might log.
  fetch('/api/tracking-session', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken() },
    body: JSON.stringify({ token: match[1] }),
  }).then(
    function (response) {
      forgetTheFragment();
      if (!response.ok) {
        show(UNAVAILABLE);
        return;
      }
      return loadRecipientApplication();
    },
    function () {
      // The exchange may or may not have happened, so the fragment is cleared here too rather than
      // left in history for a reload to resend.
      forgetTheFragment();
      show(UNREACHABLE);
    }
  );
})();

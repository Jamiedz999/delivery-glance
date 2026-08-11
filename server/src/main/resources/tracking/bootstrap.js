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

  // The Recipient application, requested only from here. Nothing in the page references these
  // files, so a visitor whose exchange failed — or who never had a token — downloads neither, and
  // "the map is never loaded before a successful bootstrap" is a fact about the network rather than
  // a rule the application has to keep. Both paths are fixed rather than content-hashed, because
  // this file is inlined into the page by the server and pinned by a CSP hash: it cannot be
  // regenerated per build, so it cannot know a hash.
  function loadRecipientApplication() {
    var stylesheet = document.createElement('link');
    stylesheet.rel = 'stylesheet';
    stylesheet.href = '/track-app.css';
    document.head.appendChild(stylesheet);

    var application = document.createElement('script');
    application.type = 'module';
    application.src = '/track-app.js';
    application.onerror = function () {
      show(UNREACHABLE);
    };
    document.body.appendChild(application);
  }

  var presented = window.location.hash;
  var match = FRAGMENT.exec(presented);
  if (!match) {
    forgetTheFragment();
    if (presented === '') {
      // No fragment at all is the ordinary case, not a failure: this page removes the fragment as
      // soon as it has spent it, so every reload after the first arrives here. The grant cookie is
      // the only thing that can authorize such a visit, and the application asks with it — which is
      // also how a visitor holding no grant still gets the one unavailable answer.
      loadRecipientApplication();
      return;
    }
    // A fragment that is present and is not a capability. Nothing is sent anywhere, and no cookie
    // is consulted either: somebody presented a broken link, and answering it with a different
    // Delivery they happen to still hold a grant for would be a confusing kind of correct.
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

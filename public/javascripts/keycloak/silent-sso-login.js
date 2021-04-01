'use strict';

// Run a silent OAuth2 workflow so that the client-side JavaScript can access the user's Keycloak tokens to
// authenticate API requests. See https://www.keycloak.org/docs/latest/securing_apps/#_javascript_adapter

parent.postMessage(location.href, location.origin)

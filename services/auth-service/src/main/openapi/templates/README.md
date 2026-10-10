# Auth RestTemplate client template override

`ApiClient.mustache` is copied from OpenAPI Generator 7.25.0 at
`Java/libraries/resttemplate/ApiClient.mustache`. It contains two compatibility
changes: `HttpHeaders.headerSet()` is replaced with `HttpHeaders.entrySet()` at
both header iteration sites because Spring Framework 6.1.14 (the version managed
by Spring Boot 3.3.5) does not provide `headerSet()`. No other template logic is
changed. The auth test-client generation execution selects this directory as its
template directory; generated source stays under `target/`.

The upstream generated client's debug switch remains present and defaults to
off. Debug mode logs request URLs, headers, and bodies, so use these test clients
only with synthetic credentials and tokens; do not enable debug logging with
real credentials or cookies.

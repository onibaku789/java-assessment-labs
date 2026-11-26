# Week 2 parcel API security evidence

## Browser preflight

The browser page on `http://127.0.0.1:18099` asked the parcel API on port `18081` for permission to send a `GET /parcels/P-100` with the `Authorization` header. The API returned `403` and omitted `Access-Control-Allow-Origin`. Its CORS policy allows the page on port `18082`. The HAR excerpt retains one preflight from the browser capture and its CORS headers. [Denied-origin trace](denied-origin.har)

From `http://127.0.0.1:18082`, the browser received `200` for the preflight. The API allowed `GET` with the `Authorization` header and echoed the page's origin in `Access-Control-Allow-Origin`. The parcel lookup also returned `200`, with tracking ID `P-100`, status `IN_TRANSIT`, and destination `York`. The excerpt includes both exchanges. [Allowed-origin trace](allowed-origin.har)

Separate requests to the running API from the allowed origin confirmed that an observer received `403` and an anonymous caller received `401`.

## Controls and OWASP risks

| Control | Evidence | OWASP API Security Top 10 |
| --- | --- | --- |
| Verify the JWT signature and require a valid expiration time | The Week 2 tests reject a token signed with an untrusted key, an expired token, and a token missing `exp` with `401`. | [API2 – Broken Authentication](https://owasp.org/API-Security/editions/2023/en/0xa2-broken-authentication/) |
| Require the `parcel.read` scope for parcel lookups | The reader and observer both present valid tokens for the same parcel. Only the reader receives the record. | [API5 – Broken Function Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa5-broken-function-level-authorization/) |

Run the automated checks from the repository root with `./mvnw -pl parcel-api -Dit.test=Week02SecurityIT verify`. [Week 2 tests](../../src/test/java/study/parcel/Week02SecurityIT.java)

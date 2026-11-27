const esc = (value) =>
  value.replace(
    /[&<>"']/g,
    (ch) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&apos;",
      })[ch],
  );
const fields = "id compartmentId state";

export function requestFor({ op, protocol, id, key, code }) {
  const read = op === "get" || op === "available";
  if (protocol === "REST") {
    const base = "/v1/reservations";
    const path =
      op === "available"
        ? "/v1/compartments"
        : op === "reserve"
          ? base
          : base +
            "/" +
            encodeURIComponent(id) +
            (op === "get" ? "" : "/" + op);
    const payload =
      op === "reserve"
        ? { compartmentId: id }
        : op === "collect"
          ? { code }
          : null;
    return {
      method: read ? "GET" : "POST",
      path,
      headers: read
        ? {}
        : { "Idempotency-Key": key, "Content-Type": "application/json" },
      body: payload && JSON.stringify(payload),
    };
  }
  if (protocol === "GraphQL") {
    const field = op === "get" ? "reservation" : op;
    const variables = {};
    let query;
    if (op === "available") query = "{ available { id site size } }";
    else if (op === "get") {
      query = "query($id:UUID!){ reservation(id:$id){ " + fields + " } }";
      variables.id = id;
    } else {
      const idType = op === "reserve" ? "ID!" : "UUID!";
      const args = op === "reserve" ? "compartmentId:$id" : "id:$id";
      query =
        `mutation($id:${idType}, $key:UUID!` +
        (op === "collect" ? ", $code:String!" : "") +
        "){ " +
        field +
        "(" +
        args +
        ", requestId:$key" +
        (op === "collect" ? ", code:$code" : "") +
        "){ " +
        fields +
        " } }";
      Object.assign(variables, { id, key });
      if (op === "collect") variables.code = code;
    }
    return {
      method: "POST",
      path: "/graphql",
      headers: { "Content-Type": "application/json", Accept: "application/graphql-response+json" },
      body: JSON.stringify({ query, variables }),
    };
  }
  const args =
    (read ? "" : "<requestId>" + key + "</requestId>") +
    (op === "available" ? "" : "<id>" + esc(id) + "</id>") +
    (op === "collect" ? "<code>" + esc(code) + "</code>" : "");
  return {
    method: "POST",
    path: "/soap/lockers",
    headers: {
      "Content-Type": "text/xml",
      SOAPAction: '"urn:lockers:' + op + '"',
    },
    body:
      '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><' +
      op +
      ' xmlns="urn:lockers:v1">' +
      args +
      "</" +
      op +
      "></s:Body></s:Envelope>",
  };
}

class ApiError extends Error {
  constructor(code, message) {
    super([code, message].filter(Boolean).join(" · ") || "Request failed");
    this.code = code;
  }
}

export function decode(protocol, op, ok, text) {
  if (protocol === "SOAP" && text.trimStart().startsWith("<")) {
    const xml = new DOMParser().parseFromString(text, "application/xml");
    const value = (node, tag) =>
      node.getElementsByTagNameNS("urn:lockers:v1", tag)[0]?.textContent;
    if (!ok || xml.querySelector("parsererror"))
      throw new ApiError(value(xml, "code"), value(xml, "message") || "SOAP request failed");
    if (op === "available")
      return Array.from(
        xml.getElementsByTagNameNS("urn:lockers:v1", "items"),
        (node) => ({
          id: value(node, "id"),
          site: value(node, "site"),
          size: value(node, "size"),
        }),
      );
    return {
      id: value(xml, "id"),
      state: value(xml, "state"),
      compartmentId: value(xml, "compartmentId"),
    };
  }
  const obj = JSON.parse(text);
  if (protocol === "GraphQL" && obj.errors?.length) {
    const [error] = obj.errors;
    throw new ApiError(error.extensions?.code, error.message);
  }
  if (!ok) throw new ApiError(obj.code, obj.message);
  return protocol === "GraphQL"
    ? obj.data[op === "get" ? "reservation" : op]
    : obj;
}

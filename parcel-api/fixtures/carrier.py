"""Loopback SOAP 1.1 carrier fixture with a WSDL and a typed fault."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from xml.etree import ElementTree as ET

SOAP = "http://schemas.xmlsoap.org/soap/envelope/"
CARRIER = "urn:parcel:carrier"


class Carrier(BaseHTTPRequestHandler):
    def xml(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "text/xml; charset=utf-8")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/carrier?wsdl":
            self.xml(200, Path(__file__).with_name("carrier.wsdl").read_bytes())
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path != "/carrier":
            return self.send_error(404)
        size = int(self.headers.get("Content-Length", "0"))
        if not 0 < size <= 4096:
            return self.send_error(400)
        try:
            doc = ET.fromstring(self.rfile.read(size))
        except ET.ParseError:
            return self.send_error(400)
        request = doc.find(f"{{{SOAP}}}Body/{{{CARRIER}}}Track")
        if request is None:
            return self.send_error(400)
        found = request.text == "P-100"
        payload = ('<c:TrackResponse>IN_TRANSIT</c:TrackResponse>' if found else
                   '<s:Fault><faultcode>s:Client</faultcode><faultstring>Unknown parcel</faultstring>'
                   '<detail><c:UnknownParcel>missing</c:UnknownParcel></detail></s:Fault>')
        envelope = f'<s:Envelope xmlns:s="{SOAP}" xmlns:c="{CARRIER}"><s:Body>{payload}</s:Body></s:Envelope>'
        self.xml(200 if found else 500, envelope.encode())


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 18083), Carrier).serve_forever()

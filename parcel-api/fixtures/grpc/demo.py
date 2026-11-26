"""One real loopback unary RPC. Generate stubs before running this file."""
from concurrent.futures import ThreadPoolExecutor
import grpc
import carrier_pb2 as messages
import carrier_pb2_grpc as service


class Carrier(service.TrackingServicer):
    def Track(self, request, context):
        if request.id != "P-100":
            context.abort(grpc.StatusCode.NOT_FOUND, "Unknown parcel")
        return messages.Reply(status="IN_TRANSIT")


server = grpc.server(ThreadPoolExecutor(max_workers=2))
service.add_TrackingServicer_to_server(Carrier(), server)
port = server.add_insecure_port("127.0.0.1:0")
server.start()
try:
    with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
        reply = service.TrackingStub(channel).Track(messages.Request(id="P-100"), timeout=5)
        assert reply.status == "IN_TRANSIT"
        print(reply.status)
finally:
    server.stop(0).wait()

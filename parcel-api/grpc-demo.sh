#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
python3 -m venv .lab/grpc
py=.lab/grpc/bin/python
"$py" -m pip -q install grpcio==1.85.4 grpcio-tools==1.85.4
"$py" -m grpc_tools.protoc -Ifixtures/grpc --python_out=.lab --grpc_python_out=.lab fixtures/grpc/carrier.proto
PYTHONPATH=.lab "$py" fixtures/grpc/demo.py

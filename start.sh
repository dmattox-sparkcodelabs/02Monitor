#!/bin/bash
cd "$(dirname "$0")"
source venv/bin/activate
nohup python -m src.main --config config.yaml > /tmp/o2monitor.log 2>&1 &
echo "O2Monitor started (PID: $!)"
echo "Logs: /tmp/o2monitor.log"

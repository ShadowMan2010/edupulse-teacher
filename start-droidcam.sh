#!/usr/bin/env bash
pkill -9 -f scanner.py 2>/dev/null
sleep 1
cd /home/dhruba/edupulse
export EDUPULSE_CAMERA="http://10.146.201.67:4747/video"
exec python3 scanner/scanner.py

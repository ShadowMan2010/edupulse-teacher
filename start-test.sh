#!/usr/bin/env bash
export EDUPULSE_CAMERA="http://100.67.194.237:4747/mjpegfeed"
exec python3 /home/dhruba/edupulse/scanner/scanner.py

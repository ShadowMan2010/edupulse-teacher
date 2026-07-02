#!/usr/bin/env bash
cd /home/dhruba/edupulse
node server.js > /tmp/api.log 2>&1 &
python3 scanner/scanner.py > /tmp/scanner.log 2>&1 &
python3 bot/bot.py > /tmp/bot.log 2>&1 &

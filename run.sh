#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
javac -d . src/SynchronizationServer.java
java SynchronizationServer

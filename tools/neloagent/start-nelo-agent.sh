#!/bin/bash

/usr/local/agent-1.7.2/bin/agent.sh start

# tail -f /dev/null means don't let the container exist forever.
tail -f /dev/null

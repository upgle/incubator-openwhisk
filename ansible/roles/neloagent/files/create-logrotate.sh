#!/bin/sh

function create(){
    rm -f /etc/logrotate.d/lambda
    cat > /etc/logrotate.d/lambda << EOL
        $1/lambdalog/*.log {
            su root root
            daily
            rotate 6
            copytruncate
            compress
            delaycompress
            notifempty
            missingok
            minsize 30M
            maxsize 100M
     }
EOL
    chmod 644 /etc/logrotate.d/lambda
}
if [ $# -ne 1 ]; then
    echo "USAGE: create-logrotate.sh <WSK_TOP_LOG_DIR>"
else
    WSK_TOP_LOG_DIR=$1
    create ${WSK_TOP_LOG_DIR}
fi

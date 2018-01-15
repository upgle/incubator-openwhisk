#!/bin/bash

function deal()
{
    for file in `ls $1`
    do
        if [ "${file}" != "lambdalog" ]; then
            local path=$1"/"${file}
            if [ -d ${path} ]; then
                deal ${path}
            else
                # create hard link
                if [ ! -f "${WSK_TOP_LOG_DIR}/lambdalog/${file}" ]; then
                    ln ${path} ${WSK_TOP_LOG_DIR}/lambdalog/${file}
                else
                    # Fix bug, because when redeploy invoker, will create a new log file.
                    # So compare the inode, if not equal, delete and create a new hardlink file.
                    if [ `stat -c %i ${path}` != `stat -c %i ${WSK_TOP_LOG_DIR}/lambdalog/${file}` ]; then
                        rm -f ${WSK_TOP_LOG_DIR}/lambdalog/${file}
                        ln ${path} ${WSK_TOP_LOG_DIR}/lambdalog/${file}
                    fi
                fi
            fi
        fi
    done
}
if [ $# -ne 1 ]; then
    echo "USAGE: create-hardlink.sh <WSK_TOP_LOG_DIR>"
else
    WSK_TOP_LOG_DIR=$1
    deal ${WSK_TOP_LOG_DIR}
fi

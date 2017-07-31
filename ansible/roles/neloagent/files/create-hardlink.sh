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

#!/bin/bash

HELP_MESSAGE=$(
cat << 'EOF-heredoc'
Usage:
  sh take-in-out-check-file.sh [operate_type] [container_name] [check_file_path]

Available operate_type:
  take-in/take-out

Available container_name:
  str: docker container name

Available check_file_path:
  str: check_file_path in docker container

EOF-heredoc
)

while getopts ":h" opt; do
    case $opt in
        h)
            echo "$HELP_MESSAGE"
            exit 1
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            ;;
    esac
done

operate_type=$1
container_name=$2
check_file_path=$3
if [ -z "$operate_type" ] || [ -z "$container_name" ] || [ -z "$check_file_path" ]; then
    echo "Parameter: operate_type or container_name or check_file_path not exist, please check"
    exit 1
fi

containerExistFlag=$(docker ps | grep -E "\s${container_name}$" | wc -l)
if [ ${containerExistFlag} -gt 0 ]; then
    if [ "$operate_type" == "take-out" ]; then
        docker exec -t ${container_name} rm -f ${check_file_path}
    else
        docker exec -t ${container_name} sh -c "mkdir -p $(dirname ${check_file_path}) && touch ${check_file_path} && chmod 777 -R $(dirname ${check_file_path})"
    fi
fi

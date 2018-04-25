#!/bin/sh

rpm --import https://artifacts.elastic.co/GPG-KEY-elasticsearch

tee /etc/yum.repos.d/logstash.repo <<EOF
[logstash-6.x]
name=Elastic repository for 6.x packages
baseurl=https://artifacts.elastic.co/packages/6.x/yum
gpgcheck=1
gpgkey=https://artifacts.elastic.co/GPG-KEY-elasticsearch
enabled=1
autorefresh=1
type=rpm-md
EOF

yum install -y logstash

tee /etc/systemd/system/logstash.service <<EOF
[Unit]
Description=Logstash
Documentation=https://www.elastic.co/products/logstash
After=network.target
ConditionPathExists=/etc/logstash/conf.d/logstash.conf

[Service]
ExecStart=/usr/share/logstash/bin/logstash  -f /etc/logstash/conf.d/logstash.conf

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable logstash.service

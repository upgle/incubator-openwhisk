FROM centos:latest

# Install nelo2-agent
COPY nelo2_agent_1.7.2_x64.tar.gz /usr/local/nelo2_agent_1.7.2_x64.tar.gz
COPY start-nelo-agent.sh /start-nelo-agent.sh
RUN tar -C /usr/local -xzf /usr/local/nelo2_agent_1.7.2_x64.tar.gz \
    && rm /usr/local/nelo2_agent_1.7.2_x64.tar.gz \
    && rm -f /usr/local/agent-1.7.2/conf/* \
    && yum install -y file \
    && chmod 755 /start-nelo-agent.sh

CMD ["/bin/bash", "-c", "/start-nelo-agent.sh"]

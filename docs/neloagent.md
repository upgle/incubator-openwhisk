
# Add configuration on lambda test/dev/real env.
* Configure neloagent for corresponding env, for example, add below configuration to `ansible/environment/local/groups/all` if need to modify default value

    ```
    # values are ["lambda-test", "lambda-dev", "lambda"]
    nelo_project_name: "lambda-test"
    nelo_project_version: "1.0.1"
    # values are ["debug", "info", "warn", "error", "fatal"]
    nelo_log_level: "debug"
    ```
# Use ansible to deploy neloagent.
* Add neloagent nodes to corresponding env's ansible inventory file, for example, add below nodes to `ansible/environment/local/hosts`

    ```
    [neloagents]
    172.17.0.1 ansible_connection=local
    ```
* Build neloagent docker image: `gradlew :tools:neloagent:distDocker`
* Deploy neloagent container, for example: `ansible-playbook -i environments/local  neloagent.yml`

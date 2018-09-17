# Deploy Scripts 

This directory holds some scripts to assist in deploying the full stack to a single node 
(probably on the cloud) for demo and integration testing purposes. This has been built to 
run on an Ubuntu Server (16.04 LTS) and might need some modification for other environments.

This is offered as an alternative to the 'build/nodes/runnodes' script, 
which sometimes suffers from  timeout problems when starting a large number of nodes. 


# Steps 

## Create server 

Deploy a cloud server. We used a Standard GS1 instance on Azure (2 vcpus, 28 GB memory)
Open ports as necessary. At a minimum 22 for SSH. We also just opened the range 10000-10999 
for access to the Corda services started 

## Patch server 

For ubuntu just run step in 'ubuntu.sh'

## Checkout code and build nodes

```bash
git clone https://github.com/user-name/repo-name.git
cd repo-name
./gradlew clean deployNodes 
```

_note, deployNodes will take a few minutes_ 

## Running nodes

The use of 'build/nodes/runnodes' is not recommended. It is has startup order 
problems when starting so many nodes and web servers. Instead use the bash scripts in the 
'deploy' directory (_you must run these from this directory_). To start a single 
node run 

```bash
./runNode.sh CLIENT-C01 10020
```

This script will shutdown any existing services for the node and then start the node followed by 
its web server. The script will echo to the console, but for details as to what is going on 
refer to the logs, which in this example are at 'build/nodes/CLIENT-C01/logs'

Note that the port number must match that defined in node configuration (see the 'deployNodes' task
in 'build.gradle'). 

Once started the web console should be accessible, in this case at http://mycloudserver:10023

The 'runAll.sh' script will start all nodes. 

## Joining the network 

Run the script below to request and approve membership for each of the nodes

```bash
./joinNetwork.sh
``` 


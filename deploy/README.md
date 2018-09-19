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
for access to the Corda services started.

## Patch server 

Update JDK etc as required for Corda. For Ubuntu, just run the steps in 'ubuntu.sh'.

## Checkout code and build nodes

```bash
git clone https://github.com/user-name/repo-name.git
cd repo-name
./gradlew clean deployNodes 
```

_note, deployNodes will take a few minutes_ 

## Running nodes

### Single Node

The use of 'build/nodes/runnodes' is not recommended. It has startup order 
problems when starting so many nodes and web servers. Instead use the bash scripts in the 
'deploy' directory (**you must run these from this directory**). To start a single 
node run 

```bash
./runNode.sh CLIENT-C01 10020
```

This script will shutdown any existing services for the node and then start the node followed by 
its web server. The script will echo to the console, but for details as to what is going on 
refer to the logs, which in this example are at 'build/nodes/CLIENT-C01/logs'.

Note that the port number must match that defined in node configuration (see the 'deployNodes' task
in 'build.gradle'). 

Once started the web console should be accessible, in this case at http://mycloudserver:10023

### All nodes

Force everything to shutdown with the rather brutal killall. 

```bash
killall -9 java 
```

Then 

```bash
./runAll.sh
```

The script will start all nodes sequentially and has simple timing loops to control startup. It will 
take a while (10 - 15 minutes) to start all nodes. All output will echo to this console and for reliability 
it is recommended that the console window is kept visible on the host (avoid sleeps)
whilst the nodes are starting. It can be closed afterwards.


## Joining the network 

Run the seeder to request and approve membership for each of the nodes and also setup initial
test trades

```bash
java -jar lib/seeder.jar 
 ``` 


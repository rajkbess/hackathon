#!/usr/bin/env bash

###############################################################################
# Will run a single node with the Corda App and its web server. Must have
# run ./gradlew deployNodes beforehand to create apps in the 'build/nodes'
# directory. Will kill any existing processes for the node. Assumes that
# the node has a consistent port numbering scheme (see build.gradle)
#
# Example
# ./runNode CLIENT-C01 10020
#
#
###############################################################################

NODENAME=$1
BASEPORT=$2


cd ../build/nodes/$NODENAME

echo "Killing existing $NODENAME processes"
for pid in $(ps -ef | grep "java" | grep "$NODENAME" | awk '{print $2}'); do kill -9  $pid; done

echo "Starting Node $NODENAME"
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -Dname=$NODENAME -Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$((BASEPORT + 5)) -javaagent:drivers/jolokia-jvm-1.6.0-agent.jar=port=$((BASEPORT + 7)),logHandlerClass=net.corda.node.JolokiaSlf4jAdapter -jar corda.jar --no-local-shell &

echo "Waiting for Node $NODENAME to start"
while ! echo exit | nc localhost $((BASEPORT + 1)); do sleep 10 && echo "waiting on $NODENAME"; done

#extra sleep just to be sure
sleep 10

echo "Starting web server $NODENAME"
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -Dname=$NODENAME -Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$((BASEPORT + 6)) -jar corda-webserver.jar&

cd .
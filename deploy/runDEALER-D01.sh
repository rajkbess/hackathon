#!/usr/bin/env bash

export NODENAME=CLIENT-C01
cd ../build/nodes/$NODENAME

echo "Starting Node $NODENAME"
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -Dname=DEALER-D01 -Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5007 -javaagent:drivers/jolokia-jvm-1.3.7-agent.jar=port=7007,logHandlerClass=net.corda.node.JolokiaSlf4jAdapter -jar corda.jar --no-local-shell

echo "Waiting for Node $NODENAME to start"
while ! echo exit | nc localhost 10009; do sleep 10 && echo "waiting on $NODENAME"; done

#extra sleep just to be sure
sleep 10

echo "Starting web server $NODENAME"
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -Dname=DEALER-D01 -Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5008 -jar corda-webserver.jar

cd .
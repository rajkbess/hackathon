#!/usr/bin/env bash

###############################################################################
# Will run the Notary.  Must have
# run ./gradlew deployNodes beforehand to create apps in the 'build/nodes'
# directory.
#
###############################################################################


cd ../build/nodes/Notary

echo "Starting Notary"
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -Dname=Notary -Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5009 -javaagent:drivers/jolokia-jvm-1.6.0-agent.jar=port=7009,logHandlerClass=net.corda.node.JolokiaSlf4jAdapter -jar corda.jar --no-local-shell &

cd .
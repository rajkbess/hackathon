#!/usr/bin/env bash

###############################################################################
# Will the notary and full set of nodes. As they start
# sequentially it will take a while.
#
#
###############################################################################

./runNotary.sh && sleep 60

./runNode.sh BNO-DTN 10010 && sleep 60
./runNode.sh CCP-P01 10100 && sleep 60

./runNode.sh CLIENT-C01 10020 && sleep 60
./runNode.sh CLIENT-C02 10030 && sleep 60
./runNode.sh CLIENT-C03 10040 && sleep 60
./runNode.sh CLIENT-C04 10050 && sleep 60
./runNode.sh CLIENT-C05 10060 && sleep 60

./runNode.sh DEALER-D01 10070 && sleep 60
./runNode.sh DEALER-D02 10080 && sleep 60
./runNode.sh DEALER-D03 10090 && sleep 60

echo "All nodes started"


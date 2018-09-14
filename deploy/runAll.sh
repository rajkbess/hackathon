#!/usr/bin/env bash

###############################################################################
# Will the notary and full set of nodes. As they started
# sequentially it will take a while.
#
#
###############################################################################

./runNotary.sh
./runNode.sh BNO-DTN 10010
./runNode.sh CCP-P01 10100

./runNode.sh CLIENT-C01 10020
./runNode.sh CLIENT-C02 10030
./runNode.sh CLIENT-C03 10040
./runNode.sh CLIENT-C04 10050
./runNode.sh CLIENT-C05 10060

./runNode.sh DEALER-D01 10070
./runNode.sh DEALER-D02 10080
./runNode.sh DEALER-D03 10090


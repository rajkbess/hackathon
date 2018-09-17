#!/usr/bin/env bash

###############################################################################
# Run this to request and approve membership for each node
# Only needs to be run once.
#
###############################################################################


# client request to join network
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10023/api/memberApi/requestMembership
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10033/api/memberApi/requestMembership
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10043/api/memberApi/requestMembership
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10053/api/memberApi/requestMembership
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10063/api/memberApi/requestMembership

# dealer requests to join network
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10073/api/memberApi/requestMembership
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10083/api/memberApi/requestMembership
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10093/api/memberApi/requestMembership

# CCP join network
curl -X POST -H "Content-Type: application/json" --data @../integration-tests/src/test/resources/testData/network-definition.json http://localhost:10103/api/memberApi/requestMembership


# BNO approves membership requests
curl -X POST -H "Content-Type: text/plain" --data "O=CLIENT-C01,L=London,C=GB"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=CLIENT-C02,L=London,C=GB"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=CLIENT-C03,L=London,C=GB"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=CLIENT-C04,L=London,C=GB"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=CLIENT-C05,L=London,C=GB"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=DEALER-D01,L=New York,C=US"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=DEALER-D02,L=New York,C=US"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=DEALER-D03,L=New York,C=US"  http://localhost:10013/api/bnoApi/activateMembership
curl -X POST -H "Content-Type: text/plain" --data "O=CCP-P01,L=New York,C=US"  http://localhost:10013/api/bnoApi/activateMembership


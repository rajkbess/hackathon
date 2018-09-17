![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Derivatives Trading Network

## Pre-Requisites

   * This project uses Corda 4.0 to take advantage of reference states
   * As a consequence this project uses Kotlin 1.2

## Description

This project sets up a network of clients, dealers and central counterparties that can trade together using CDM events. This business network is governed by Business Network Operator, which is also part of the project.

Each node is defined by 

   * legalEntityId
   * type
   * name
   * list of (partyId, account) pairs
   
This definition is then used to map `partyId` on a CDM event to a Corda party

## Sample usage

The EndToEndTest.kt file sets up a network of dealers, clients and ccp, governed by a Business Network Operator. Please refer to these tests for reference on

* How membership in the business network is requested and granted
* How CDM events are stored by nodes on the ledger
* How CDM events are queried from the ledger

## Running the whole network

You can use the well-known sequence of 

* gradlew clean deploynodes
* cd build/nodes
* runnodes

To start a network of the Barclays Hackathon clients, dealers, ccp and BNO. You will then have 1 BNO, 1 CCP, 5 clients and 3 dealers nodes running. Each of these nodes represents one legal entity.

To quickly run the initial network joining process

```bash
cd deploy 
./joinNetwork.sh 
```

Running so many nodes locally may be troublesome, in this case we recommend either:

* Setting up a cloud server, using the scripts and processes documented in the 'deploy' folder
* Editing 'build.gradle' to remove some of the node (will need to rerun 'gradlew clean deploynodes') 
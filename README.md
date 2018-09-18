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

### There are multiple options how to run the nodes for demo, development or test purposes

#### Option 1: Run all nodes and their webservers in one JVM from Intelli J

* On Mac just run the "Mac Only: Run Network" configuration
* On Windows run the "Windows: Run Network" JUnit test configuration

This will start the Barclays clients, dealers, CCP and the BNO. It will also set up the memberships in the business network and place the Barclays trades on the ledger.

#### Option 2: Run all nodes in their separate JVMs

There are two options. You can use the well-known sequence of:

```bash
./gradlew clean deploynodes
cd build/nodes
./runnodes
```

However this may not run reliably with the full set of nodes (_sometimes nodes fail to start in time_). In this case 
a process for quickly deploying to a cloud server is documented in the 'deploy' folder, simply refer to 
the README. Alternatively try editing 'build.gradle' to remove some of the nodes (_will need to rerun 
the steps above afterwards_).  

Once running the full network will then have 1 BNO, 1 CCP, 5 clients and 3 dealers nodes running. 
Each of these nodes represents one legal entity.

If all is running correctly the following will be available:

* [BNO-DTN](http://localhost:10013)
* [CCP-P01](http://localhost:10103)
* [CLIENT-C01](http://localhost:10023)
* [CLIENT-C02](http://localhost:10033)
* [CLIENT-C03](http://localhost:10043)
* [CLIENT-C04](http://localhost:10053)
* [CLIENT-C05](http://localhost:10063)
* [DEALER-D01](http://localhost:10073)
* [DEALER-D02](http://localhost:10083)
* [DEALER-D03](http://localhost:10093)

The notary RPC process is available on port 10003 but there is no web UI.

#### Option 2 - initial joining process

Nodes need to request membership, which is then approved by the BNO. To quickly run these steps for all nodes:

```bash
cd deploy 
./joinNetwork.sh 
```

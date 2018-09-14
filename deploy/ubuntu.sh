#!/usr/bin/env bash

###############################################################################
# Steps to patch an Ubuntu (16.04) server with the necessary additional tools
###############################################################################

sudo apt-get update -yqq

sudo apt-get install -yqq \
    apt-transport-https ca-certificates curl software-properties-common \
    openjdk-8-jdk unzip

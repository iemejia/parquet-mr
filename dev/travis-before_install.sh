#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

################################################################################
# This script gets invoked by .travis.yml in the before_install step
################################################################################

set -ex
date
sudo apt-get update -qq
sudo apt-get install -qq build-essential pv autoconf automake libtool curl make \
   g++ unzip libboost-dev libboost-test-dev libboost-program-options-dev \
   libevent-dev automake libtool flex bison pkg-config g++ libssl-dev xmlstarlet
date
pwd
export THRIFT_HOME=$HOME/thrift-${THRIFT_VERSION}
echo "$THRIFT_HOME"
ls "$THRIFT_HOME"
if [ ! -d "$THRIFT_HOME" ]; then
  wget -nv -P "$HOME" "http://archive.apache.org/dist/thrift/${THRIFT_VERSION}/thrift-${THRIFT_VERSION}.tar.gz"
  tar xf "$HOME/thrift-${THRIFT_VERSION}.tar.gz" -C "$HOME"
  cd "$THRIFT_HOME"
  chmod +x ./configure
  ./configure --disable-gen-erl --disable-gen-hs --without-ruby --without-haskell --without-erlang --without-php --without-nodejs
fi
cd "$THRIFT_HOME"
sudo make install
date
branch_specific_script="dev/travis-before_install-${TRAVIS_BRANCH}.sh"
if [[ -e "$branch_specific_script" ]]
then
  . "$branch_specific_script"
fi
date

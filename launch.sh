#!/usr/bin/bash

(
  cd japi
  git pull
)

git pull
./gradlew bootRun

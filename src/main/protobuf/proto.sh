#!/bin/bash

protoc --java_out=../java/ ./GooglePlay.proto
protoc --java_out=../java/ ./GoogleServicesFramework.proto
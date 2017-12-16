FROM hseeberger/scala-sbt AS build

WORKDIR /build

ADD . /build

RUN sbt universal:packageBin
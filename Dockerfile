FROM hseeberger/scala-sbt AS build

RUN sbt update

WORKDIR /build

ADD . /build

RUN sbt universal:packageBin

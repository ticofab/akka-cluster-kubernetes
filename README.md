# Akka Cluster Kubernetes

This project is a working example of achieving Elasticity (in the sense of the [Reactive Manifesto](https://www.reactivemanifesto.org)) using Akka Cluster and Kubernetes.

Elasticity is the ability of a system to scale its resources up and down according to the present need. We want to use just the right amount: nothing more, nothing less.

We combine custom resource metrics with logic to automatically adjust the configuration of the underlying cloud infrastructure.    

## Usage

First, package the app localy in a Docker container:

```sbt docker:publishLocal```

Upload the image to your Kubernetes project:

```gcloud docker -- push <your_project>:latest```

Start the master node with:

```kubectl apply -f master.yaml```

Now you can access nodes' logs. When done, shut down:

```kubectl delete deployment akka-master```  

## License

    Copyright 2018 Fabio Tiriticco, Adam Sandor

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


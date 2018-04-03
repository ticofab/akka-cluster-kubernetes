package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.{Actor, ActorLogging, Props}
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecBuilder
import io.fabric8.kubernetes.client.{DefaultKubernetesClient, NamespacedKubernetesClient}

import scala.collection.JavaConverters

// TODO: tune how fast workers are created
// TODO: insert switch to not use kubernetes
// TODO: provide some readiness probe

/**
  * This guy knows the K8S ways, while the rest of the app is K8S-agnostic.
  */
class KubernetesController extends Actor with ActorLogging {

  val client: NamespacedKubernetesClient = new DefaultKubernetesClient().inNamespace(System.getenv("namespace"))
  val role = "worker"
  val workerDeploymentName = s"akka-$role"

  override def postStop(): Unit = {
    super.postStop()

    log.debug("Stopping controller - deleting all workers")

    client.extensions().deployments().withName(workerDeploymentName).delete()
  }

  override def receive = {

    case AddNode =>

      log.debug("AddNode")

      val workers = client.extensions.deployments.withName(workerDeploymentName)

      if (workers.get != null) {

        // scale up the existing deployment by one replica
        //        val status = apps.get.getStatus
        //        val currentReplicas = status.getCurrentReplicas
        //        val readyReplicas = status.getReadyReplicas

        val replicas = workers.get.getSpec.getReplicas + 1

        if (replicas < 2) {
          log.debug(s"Scaling up Deployment $workerDeploymentName to $replicas replicas")

          workers.scale(replicas)
        } else {
          log.debug("Can't scale up. Reached maximum number of replicas")
        }
      } else {

        // create new stateful set
        log.debug(s"Creating new Deployment $workerDeploymentName")

        val workerSpec = getWorkerSpec(role)
        val nameMetadata = new ObjectMetaBuilder().withName(workerDeploymentName).build
        client.extensions().deployments()
          .createNew()
          .withMetadata(nameMetadata)
          .withSpec(workerSpec)
          .done
      }

    case RemoveNode =>
      log.debug("RemoveNode")

      val workers = client.extensions.deployments.withName(workerDeploymentName)

      if (workers.get != null) {

        val replicas = workers.get.getSpec.getReplicas - 1

        if (replicas >= 1) {

          log.debug(s"Scaling down Deployment $workerDeploymentName to $replicas replicas")

          workers.scale(replicas)

        } else {
          log.debug("Only one replica remains in the Deployment - not scaling down")
        }
      } else {
        log.debug("Deployment doesn't exist, not scaling down")
      }


  }

  def getWorkerSpec(role: String) = {

    val envVars = JavaConverters.seqAsJavaList(
      List[EnvVar](new EnvVarBuilder().withName("ROLE").withValue("worker").build(),
                   new EnvVarBuilder().withName("POD_IP").withNewValueFrom().withFieldRef(
                      new ObjectFieldSelectorBuilder().withFieldPath("status.podIP").build()).endValueFrom().build()))

    val labels = JavaConverters.mapAsJavaMap(Map("app" -> s"akka-$role", "role" -> role, "cluster" -> "cluster1"))

    val containerPort = new ContainerPortBuilder().withContainerPort(2551).build()

    // commented out code about the readiness probe for now
    //
    //    val httpGet = new HTTPGetActionBuilder()
    //      .withPath("/")
    //      .withPort(new IntOrString(8080))
    //      .build
    //
    //    val readinessProbe = new ProbeBuilder()
    //      .withHttpGet(httpGet)
    //      .withFailureThreshold(5)
    //      .withTimeoutSeconds(60)
    //      .build

    val container = new ContainerBuilder()
      .withName(s"akka-$role")
      .withImage(System.getenv("WORKER_IMAGE"))
      .withImagePullPolicy("Always")
      // .withReadinessProbe(readinessProbe)
      .withEnv(envVars)
      .withPorts(JavaConverters.seqAsJavaList[ContainerPort](List(containerPort)))
      .build

    val spec = new PodSpecBuilder()
      .withTerminationGracePeriodSeconds(10L)
      .withContainers(container)
      .build

    val labelMetadata = new ObjectMetaBuilder().withLabels(labels).build

    val labelSelector = new LabelSelectorBuilder().withMatchLabels(labels).build

    val podTemplate = new PodTemplateSpecBuilder()
      .withMetadata(labelMetadata)
      .withSpec(spec)
      .build

    new DeploymentSpecBuilder()
      .withSelector(labelSelector)
      .withReplicas(1)
      .withTemplate(podTemplate)
      .build
  }

}

object KubernetesController {
  def apply(): Props = Props(new KubernetesController)
}

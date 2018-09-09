package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.{Actor, Props}
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecBuilder
import io.fabric8.kubernetes.client.{DefaultKubernetesClient, NamespacedKubernetesClient}
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport
import io.ticofab.akkaclusterkubernetes.config.Config

import scala.collection.JavaConverters

/**
  * This guy knows the K8S ways, while the rest of the app is K8S-agnostic.
  */
class KubernetesController extends Actor with CustomLogSupport {

  val client: NamespacedKubernetesClient = new DefaultKubernetesClient().inNamespace(System.getenv("namespace"))
  val workerDeploymentName = s"akka-worker"

  override def postStop(): Unit = {
    super.postStop()
    info(logJson("Stopping controller - deleting all workers"))
    client.extensions().deployments().withName(workerDeploymentName).delete()
  }

  override def receive = {

    case AddNode =>

      val workers = client.extensions.deployments.withName(workerDeploymentName)

      // check if the deployment is there
      if (workers.get != null) {

        // scale up the replicas
        val currentReplicas = workers.get.getSpec.getReplicas

        if ((currentReplicas + 1) < Config.kubernetes.`max-replicas`) {
          val newReplicasAmount = currentReplicas + 1
          info(logJson(s"We currently have $currentReplicas nodes, scaling up Deployment $workerDeploymentName to $newReplicasAmount replicas"))
          workers.scale(newReplicasAmount)
        } else {
          info(logJson(s"Can't scale up. Reached maximum number of $currentReplicas replicas."))
        }

      } else {

        // create new deployment
        info(logJson(s"Creating new Deployment $workerDeploymentName"))

        val workerSpec = getWorkerSpec
        val nameMetadata = new ObjectMetaBuilder().withName(workerDeploymentName).build
        client.extensions().deployments()
          .createNew()
          .withMetadata(nameMetadata)
          .withSpec(workerSpec)
          .done
      }

    case RemoveNode =>

      val workers = client.extensions.deployments.withName(workerDeploymentName)

      if (workers.get != null) {

        // scale the replicas down
        val replicas = workers.get.getSpec.getReplicas - 1

        if (replicas >= 1) {
          info(logJson(s"Scaling down Deployment $workerDeploymentName to $replicas replicas"))
          workers.scale(replicas)
        } else {
          info(logJson("Only one replica remains in the Deployment - not scaling down"))
        }
      } else {

        // nothing to scale down
        info(logJson("Deployment doesn't exist, not scaling down"))

      }

  }

  def getWorkerSpec = {

    val role = "worker"
    val envVars = JavaConverters.seqAsJavaList(
      List[EnvVar](
        new EnvVarBuilder().withName("ROLE").withValue(role).build(),
        new EnvVarBuilder().withName("POD_IP").withNewValueFrom().withFieldRef(
          new ObjectFieldSelectorBuilder().withFieldPath("status.podIP").build()).endValueFrom().build()))

    val labels = JavaConverters.mapAsJavaMap(Map("app" -> s"akka-$role", "role" -> role, "cluster" -> "cluster1"))

    val containerPort = new ContainerPortBuilder().withContainerPort(2551).build()

    val container = new ContainerBuilder()
      .withName(s"akka-$role")
      .withImage(System.getenv("WORKER_IMAGE"))
      .withImagePullPolicy("Always")
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

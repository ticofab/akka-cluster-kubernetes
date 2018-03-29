package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.{Actor, ActorLogging, Props}
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.api.model.extensions.StatefulSetSpecBuilder
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
  val statefulSetName = s"akka-$role"

  override def postStop(): Unit = {
    super.postStop()

    log.debug("Stopping controller - deleting all workers")

    client.apps.statefulSets().withName(statefulSetName).delete()
  }

  override def receive = {

    case AddNode =>

      log.debug("AddNode")

      val apps = client.apps.statefulSets.withName(statefulSetName)

      if (apps.get != null) {

        // scale up the existing stateful set by one node
        //        val status = apps.get.getStatus
        //        val currentReplicas = status.getCurrentReplicas
        //        val readyReplicas = status.getReadyReplicas

        val replicas = apps.get.getSpec.getReplicas + 1

        if (replicas < 5) {
          log.debug("Scaling up StatefulSet {} to {} replicas", statefulSetName, $replicas)

          apps.scale(replicas)
        } else {
          println("Can't scale up. Reached maximum number of replicas")
        }
      } else {

        // create new stateful set
        println(s"Creating new stateful set $statefulSetName")

        val newSetSpec = getNewStatefulSetSpec(role)
        val nameMetadata = new ObjectMetaBuilder().withName(statefulSetName).build
        client.apps().statefulSets()
          .createNew()
          .withMetadata(nameMetadata)
          .withSpec(newSetSpec)
          .done
      }

    case RemoveNode =>
      log.debug("RemoveNode")

      val apps = client.apps.statefulSets.withName(statefulSetName)

      if (apps.get != null) {

        // scale up the existing stateful set by one node

        val replicas = apps.get.getSpec.getReplicas - 1

        if (replicas >= 1) {
          log.debug("Scaling down StatefulSet {} to {} replicas", statefulSetName, replicas)
          apps.scale(replicas)
        } else log.debug("Only one replica remains in the StatefulSet - not scaling down")

      } else log.debug("Statefulset doesn't exist, not scaling down")


  }

  def getNewStatefulSetSpec(role: String) = {

    val envVars = JavaConverters.seqAsJavaList(
      List[EnvVar](new EnvVarBuilder().withName("ROLE").withValue("worker").build))

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
      .withImage(s"eu.gcr.io/adam-akka/akka-cluster-kubernetes:" + System.getenv("version"))
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

    new StatefulSetSpecBuilder()
      .withSelector(labelSelector)
      .withPodManagementPolicy("Parallel")
      .withReplicas(1)
      .withServiceName("akka")
      .withTemplate(podTemplate)
      .build
  }

}

object KubernetesController {
  def apply(): Props = Props(new KubernetesController)
}

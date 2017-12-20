package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, Props}
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.api.model.extensions.StatefulSetSpecBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ticofab.akkaclusterkubernetes.actor.KubernetesController.{AddNode, RemoveNode}

import scala.collection.JavaConverters

// TODO this guys knows the kubernetes ways
class KubernetesController extends Actor {

  override def receive = {

    case AddNode =>

      println("AddNode")

      val client = new DefaultKubernetesClient().inNamespace(System.getenv("namespace"))
      val role = "worker"
      val statefulSetName = s"akka-$role"
      val apps = client.apps.statefulSets.withName(statefulSetName)

      if (apps.get != null) {

        // scale up the existing stateful set by one node

        val replicas = apps.get.getSpec.getReplicas + 1

        if (replicas < 5) {
          println(s"Scaling up StatefulSet $statefulSetName to $replicas replicas")

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

    case RemoveNode => ???

  }

  def getNewStatefulSetSpec(role: String) = {

    val envVars = JavaConverters.seqAsJavaList(
      List[EnvVar](new EnvVarBuilder().withName("ROLE").withValue("worker").build))

    val labels = JavaConverters.mapAsJavaMap(Map("app" -> s"akka-$role", "role" -> role))

    val containerPort = new ContainerPortBuilder().withContainerPort(2551).build()

    val container = new ContainerBuilder()
      .withName(s"akka-$role")
      .withImage(s"eu.gcr.io/adam-akka/akka-cluster-kubernetes:" + System.getenv("version"))
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

  case object AddNode

  case object RemoveNode

}

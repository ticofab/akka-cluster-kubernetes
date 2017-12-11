package io.ticofab.akkaclusterkubernetes.actor

import java.util

import akka.actor.{Actor, Props}
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.api.model.extensions.StatefulSetSpecBuilder
import io.fabric8.kubernetes.client.{ConfigBuilder, DefaultKubernetesClient, KubernetesClientException}
import io.ticofab.akkaclusterkubernetes.actor.KubernetesController.{AddNode, RemoveNode}

import scala.collection.JavaConverters
import scala.io.Source

// TODO this guys knows the kubernetes ways
class KubernetesController extends Actor {

  override def receive = {

    case AddNode =>
      println(s"AddNode: $AddNode")
      val client = new DefaultKubernetesClient()

      val role = "worker"
      val statefulSetName = s"akka-$role"
      val statefulSet = client.apps().statefulSets().withName(statefulSetName).get()

      if (statefulSet != null) {
        println("Scaling up StatefulSet " + statefulSetName)
        //scale up the existing statefulset
        statefulSet.getSpec.setReplicas(statefulSet.getSpec.getReplicas + 1)
      } else {
        println("Creating new Statefulset " + statefulSetName)
        //create new statefulset
        val envVars = JavaConverters.seqAsJavaList(List[EnvVar](
          new EnvVarBuilder().withName("ROLE").withValue("worker").build()))

        val labels = JavaConverters.mapAsJavaMap(Map("app" -> s"akka-$role", "role" -> role))

        val podTemplate = new PodTemplateSpecBuilder()
          .withMetadata(new ObjectMetaBuilder().withLabels(labels).build())
          .withSpec(new PodSpecBuilder()
            .withTerminationGracePeriodSeconds(10L)
            .withContainers(new ContainerBuilder()
              .withName(s"akka-$role")
              .withImage(s"adamsandor83/akka-$role")
              .withImagePullPolicy("Always")
              .withEnv(envVars)
              .withPorts(
                JavaConverters.seqAsJavaList[ContainerPort](List(new ContainerPortBuilder().withContainerPort(2551).build())
                )
              ).build()
            ).build()
          ).build()

        client.inNamespace("default").apps().statefulSets()
          .createNew()
          .withMetadata(
            new ObjectMetaBuilder()
              .withName(statefulSetName)
              .build())
          .withSpec(
            new StatefulSetSpecBuilder()
              .withSelector(
                new LabelSelectorBuilder()
                  .withMatchLabels(labels)
                  .build()
              )
              .withPodManagementPolicy("Parallel")
              .withReplicas(1)
              .withServiceName("akka")
              .withTemplate(podTemplate)
              .build()).done()
      }

    case RemoveNode => ???

  }

}

object KubernetesController {
  def apply(): Props = Props(new KubernetesController)

  case object AddNode

  case object RemoveNode

}

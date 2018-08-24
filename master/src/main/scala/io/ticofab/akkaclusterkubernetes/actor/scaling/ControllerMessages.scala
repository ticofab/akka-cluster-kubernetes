package io.ticofab.akkaclusterkubernetes.actor.scaling

sealed trait ControllerMessage

case object AddNode extends ControllerMessage

case object RemoveNode extends ControllerMessage


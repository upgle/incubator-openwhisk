package whisk.core.lambda

import whisk.common.{LoggingMarkers, MetricEmitter}
import whisk.core.entity.{FullyQualifiedEntityName, Identity, WhiskActivation}
import spray.json.DefaultJsonProtocol._

object LambdaLogging {

  def emitActivationMetric(whiskActivation: WhiskActivation): Unit = {

    for (path <- whiskActivation.annotations.get("path");
         limits <- whiskActivation.annotations.get("limits");
         memory <- limits.asJsObject.fields.get("memory");
         duration <- whiskActivation.duration) yield {

      val namespace = whiskActivation.namespace.asString
      val memoryAsString = memory.toString()
      val action = path.convertTo[String]

      MetricEmitter.emitHistogramMetric(LoggingMarkers.INVOKER_ACTIVATION_DURATION(namespace, memoryAsString), duration)
      MetricEmitter.emitHistogramMetric(LoggingMarkers.INVOKER_ACTIVATION_ACTION_DURATION(namespace, action), duration)

      // if error occurs
      if (!whiskActivation.response.isSuccess) {
        MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_ACTIVATION_ERROR(namespace))
        MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_ACTIVATION_ERROR(namespace, action))
      }
    }
  }

  def emitRunMetric(namespace: String, actionFullPath: String, memoryMegabyte: String, execKind: String): Unit = {
    MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_RUN(namespace))
    MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_RUN(namespace, actionFullPath))
    MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_RUN_INFO(namespace, memoryMegabyte, execKind))
  }

  def emitTriggerMetric(user: Identity, entityName: FullyQualifiedEntityName): Unit = {
    val namespace = user.namespace.name.asString
    MetricEmitter.emitCounterMetric(LoggingMarkers.CONTROLLER_TRIGGER_FIRE(namespace))
    MetricEmitter.emitCounterMetric(LoggingMarkers.CONTROLLER_TRIGGER_FIRE(namespace, entityName.asString))
  }

}

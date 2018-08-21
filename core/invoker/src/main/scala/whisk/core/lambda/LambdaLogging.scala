package whisk.core.lambda

import whisk.common.{LoggingMarkers, MetricEmitter}
import whisk.core.containerpool.Run
import whisk.core.entity.WhiskActivation
import spray.json.DefaultJsonProtocol._

object LambdaLogging {

  def emitActivationMetric(whiskActivation: WhiskActivation): Unit = {

    for (path <- whiskActivation.annotations.get("path");
         limits <- whiskActivation.annotations.get("limits");
         memory <- limits.asJsObject.fields.get("memory");
         duration <- whiskActivation.duration) yield {
      val namespace = whiskActivation.namespace.asString
      val action = path.convertTo[String]
      val memoryAsString = memory.toString()

      MetricEmitter.emitHistogramMetric(LoggingMarkers.INVOKER_ACTIVATION_DURATION(namespace, memoryAsString), duration)
      MetricEmitter.emitHistogramMetric(LoggingMarkers.INVOKER_ACTIVATION_ACTION_DURATION(namespace, action), duration)

      // if error occurs
      if (!whiskActivation.response.isSuccess) {
        MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_ACTIVATION_ERROR(namespace))
        MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_ACTIVATION_ERROR(namespace, action))
      }
    }
  }

  def emitRunMetric(job: Run): Unit = {
    MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_RUN(job.msg.user.namespace.name.asString))
    MetricEmitter.emitCounterMetric(
      LoggingMarkers.INVOKER_RUN(job.msg.user.namespace.name.asString, job.msg.action.fullPath.asString))
    MetricEmitter.emitCounterMetric(
      LoggingMarkers.INVOKER_RUN_INFO(
        job.msg.user.namespace.name.asString,
        job.action.limits.memory.megabytes.toString,
        job.action.exec.kind))
  }

}
